/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.room.processor

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Ignore
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.ext.extendsBoundOrSelf
import androidx.room.ext.getAllFieldsIncludingPrivateSupers
import androidx.room.ext.getAllMethodsIncludingSupers
import androidx.room.ext.hasAnnotation
import androidx.room.ext.hasAnyOf
import androidx.room.ext.isAssignableWithoutVariance
import androidx.room.ext.isCollection
import androidx.room.ext.toAnnotationBox
import androidx.room.ext.typeName
import androidx.room.kotlin.KotlinMetadataElement
import androidx.room.kotlin.descriptor
import androidx.room.processor.ProcessorErrors.CANNOT_FIND_GETTER_FOR_FIELD
import androidx.room.processor.ProcessorErrors.CANNOT_FIND_SETTER_FOR_FIELD
import androidx.room.processor.ProcessorErrors.CANNOT_FIND_TYPE
import androidx.room.processor.ProcessorErrors.POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME
import androidx.room.processor.autovalue.AutoValuePojoProcessorDelegate
import androidx.room.processor.cache.Cache
import androidx.room.vo.CallType
import androidx.room.vo.Constructor
import androidx.room.vo.EmbeddedField
import androidx.room.vo.EntityOrView
import androidx.room.vo.Field
import androidx.room.vo.FieldGetter
import androidx.room.vo.FieldSetter
import androidx.room.vo.Pojo
import androidx.room.vo.PojoMethod
import androidx.room.vo.Warning
import androidx.room.vo.columnNames
import androidx.room.vo.findFieldByColumnName
import asTypeElement
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.auto.value.AutoValue
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier.ABSTRACT
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PROTECTED
import javax.lang.model.element.Modifier.PUBLIC
import javax.lang.model.element.Modifier.STATIC
import javax.lang.model.element.Modifier.TRANSIENT
import javax.lang.model.element.Name
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter

/**
 * Processes any class as if it is a Pojo.
 */
class PojoProcessor private constructor(
    baseContext: Context,
    val element: TypeElement,
    val bindingScope: FieldProcessor.BindingScope,
    val parent: EmbeddedField?,
    val referenceStack: LinkedHashSet<Name> = LinkedHashSet(),
    val ignoredColumns: Set<String>,
    private val delegate: Delegate
) {
    val context = baseContext.fork(element)

    private val kotlinMetadata = KotlinMetadataElement.createFor(context, element)

    companion object {
        val PROCESSED_ANNOTATIONS = listOf(ColumnInfo::class, Embedded::class, Relation::class)

        val TARGET_METHOD_ANNOTATIONS = arrayOf(PrimaryKey::class, ColumnInfo::class,
            Embedded::class, Relation::class)

        fun createFor(
            context: Context,
            element: TypeElement,
            bindingScope: FieldProcessor.BindingScope,
            parent: EmbeddedField?,
            referenceStack: LinkedHashSet<Name> = LinkedHashSet(),
            ignoredColumns: Set<String> = emptySet()
        ): PojoProcessor {
            val (pojoElement, delegate) = if (element.hasAnnotation(AutoValue::class)) {
                val elementUtils = context.processingEnv.elementUtils
                val autoValueGeneratedElement = element.let {
                    val typeName = AutoValuePojoProcessorDelegate.getGeneratedClassName(it)
                    elementUtils.getTypeElement(typeName) ?: throw MissingTypeException(typeName)
                }
                autoValueGeneratedElement to AutoValuePojoProcessorDelegate(context, element)
            } else {
                element to DefaultDelegate(context)
            }

            return PojoProcessor(
                    baseContext = context,
                    element = pojoElement,
                    bindingScope = bindingScope,
                    parent = parent,
                    referenceStack = referenceStack,
                    ignoredColumns = ignoredColumns,
                    delegate = delegate)
        }
    }

    fun process(): Pojo {
        return context.cache.pojos.get(Cache.PojoKey(element, bindingScope, parent)) {
            referenceStack.add(element.qualifiedName)
            try {
                doProcess()
            } finally {
                referenceStack.remove(element.qualifiedName)
            }
        }
    }

    private fun doProcess(): Pojo {
        delegate.onPreProcess(element)

        val declaredType = MoreTypes.asDeclared(element.asType())
        // TODO handle conflicts with super: b/35568142
        val allFields = element.getAllFieldsIncludingPrivateSupers(context.processingEnv)
                .filter {
                    !it.hasAnnotation(Ignore::class) &&
                            !it.hasAnyOf(STATIC) &&
                            (!it.hasAnyOf(TRANSIENT) ||
                            it.hasAnnotation(ColumnInfo::class) ||
                            it.hasAnnotation(Embedded::class) ||
                            it.hasAnnotation(Relation::class))
                }
                .groupBy { field ->
                    context.checker.check(
                            PROCESSED_ANNOTATIONS.count { field.hasAnnotation(it) } < 2, field,
                            ProcessorErrors.CANNOT_USE_MORE_THAN_ONE_POJO_FIELD_ANNOTATION
                    )
                    if (field.hasAnnotation(Embedded::class)) {
                        Embedded::class
                    } else if (field.hasAnnotation(Relation::class)) {
                        Relation::class
                    } else {
                        null
                    }
                }

        val unfilteredMyFields = allFields[null]
                ?.map {
                    FieldProcessor(
                            baseContext = context,
                            containing = declaredType,
                            element = it,
                            bindingScope = bindingScope,
                            fieldParent = parent).process()
                } ?: emptyList()
        val myFields = unfilteredMyFields.filterNot { ignoredColumns.contains(it.columnName) }

        val unfilteredEmbeddedFields =
                allFields[Embedded::class]
                        ?.mapNotNull {
                            processEmbeddedField(declaredType, it)
                        }
                        ?: emptyList()
        val embeddedFields =
            unfilteredEmbeddedFields.filterNot { ignoredColumns.contains(it.field.columnName) }

        val subFields = embeddedFields.flatMap { it.pojo.fields }
        val fields = myFields + subFields

        val unfilteredCombinedFields =
            unfilteredMyFields + unfilteredEmbeddedFields.map { it.field }
        val missingIgnoredColumns = ignoredColumns.filterNot { ignoredColumn ->
            unfilteredCombinedFields.any { it.columnName == ignoredColumn }
        }
        context.checker.check(
                missingIgnoredColumns.isEmpty(), element,
                ProcessorErrors.missingIgnoredColumns(missingIgnoredColumns)
        )

        val myRelationsList = allFields[Relation::class]
                ?.mapNotNull {
                    processRelationField(fields, declaredType, it)
                }
                ?: emptyList()

        val subRelations = embeddedFields.flatMap { it.pojo.relations }
        val relations = myRelationsList + subRelations

        fields.groupBy { it.columnName }
                .filter { it.value.size > 1 }
                .forEach {
                    context.logger.e(element, ProcessorErrors.pojoDuplicateFieldNames(
                            it.key, it.value.map(Field::getPath)
                    ))
                    it.value.forEach {
                        context.logger.e(it.element, POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME)
                    }
                }

        val methods = MoreElements.getLocalAndInheritedMethods(element,
                context.processingEnv.typeUtils,
                context.processingEnv.elementUtils)
                .filter {
                    !it.hasAnyOf(PRIVATE, ABSTRACT, STATIC) &&
                            !it.hasAnnotation(Ignore::class)
                }
                .map { MoreElements.asExecutable(it) }
                .map {
                    PojoMethodProcessor(
                            context = context,
                            element = it,
                            owner = declaredType
                    ).process()
                }

        val getterCandidates = methods.filter {
            it.element.parameters.size == 0 && it.resolvedType.returnType.kind != TypeKind.VOID
        }

        val setterCandidates = methods.filter {
            it.element.parameters.size == 1 && it.resolvedType.returnType.kind == TypeKind.VOID
        }

        // don't try to find a constructor for binding to statement.
        val constructor = if (bindingScope == FieldProcessor.BindingScope.BIND_TO_STMT) {
            // we don't need to construct this POJO.
            null
        } else {
            chooseConstructor(myFields, embeddedFields, relations)
        }

        assignGetters(myFields, getterCandidates)
        assignSetters(myFields, setterCandidates, constructor)

        embeddedFields.forEach {
            assignGetter(it.field, getterCandidates)
            assignSetter(it.field, setterCandidates, constructor)
        }

        myRelationsList.forEach {
            assignGetter(it.field, getterCandidates)
            assignSetter(it.field, setterCandidates, constructor)
        }

        return delegate.createPojo(element, declaredType, fields, embeddedFields, relations,
                constructor)
    }

    /**
     * Retrieves the parameter names of a method. If the method is inherited from a dependency
     * module, the parameter name is not available (not in java spec). For kotlin, since parameter
     * names are part of the API, we can read them via the kotlin metadata annotation.
     * <p>
     * Since we are using an unofficial library to read the metadata, all access to that code
     * is safe guarded to avoid unexpected failures. In other words, it is a best effort but
     * better than not supporting these until JB provides a proper API.
     */
    private fun getParamNames(method: ExecutableElement): List<String> {
        val paramNames = method.parameters.map { it.simpleName.toString() }
        if (paramNames.isEmpty()) {
            return emptyList()
        }
        return kotlinMetadata?.getParameterNames(method) ?: paramNames
    }

    private fun chooseConstructor(
        myFields: List<Field>,
        embedded: List<EmbeddedField>,
        relations: List<androidx.room.vo.Relation>
    ): Constructor? {
        val constructors = delegate.findConstructors(element)
        val fieldMap = myFields.associateBy { it.name }
        val embeddedMap = embedded.associateBy { it.field.name }
        val relationMap = relations.associateBy { it.field.name }
        val typeUtils = context.processingEnv.typeUtils
        // list of param names -> matched params pairs for each failed constructor
        val failedConstructors = arrayListOf<FailedConstructor>()
        val goodConstructors = constructors.map { constructor ->
            val parameterNames = getParamNames(constructor)
            val params = constructor.parameters.mapIndexed param@{ index, param ->
                val paramName = parameterNames[index]
                val paramType = param.asType()

                val matches = fun(field: Field?): Boolean {
                    return if (field == null) {
                        false
                    } else if (!field.nameWithVariations.contains(paramName)) {
                        false
                    } else {
                        // see: b/69164099
                        typeUtils.isAssignableWithoutVariance(paramType, field.type)
                    }
                }

                val exactFieldMatch = fieldMap[paramName]
                if (matches(exactFieldMatch)) {
                    return@param Constructor.Param.FieldParam(exactFieldMatch!!)
                }
                val exactEmbeddedMatch = embeddedMap[paramName]
                if (matches(exactEmbeddedMatch?.field)) {
                    return@param Constructor.Param.EmbeddedParam(exactEmbeddedMatch!!)
                }
                val exactRelationMatch = relationMap[paramName]
                if (matches(exactRelationMatch?.field)) {
                    return@param Constructor.Param.RelationParam(exactRelationMatch!!)
                }

                val matchingFields = myFields.filter {
                    matches(it)
                }
                val embeddedMatches = embedded.filter {
                    matches(it.field)
                }
                val relationMatches = relations.filter {
                    matches(it.field)
                }
                when (matchingFields.size + embeddedMatches.size + relationMatches.size) {
                    0 -> null
                    1 -> when {
                        matchingFields.isNotEmpty() ->
                            Constructor.Param.FieldParam(matchingFields.first())
                        embeddedMatches.isNotEmpty() ->
                            Constructor.Param.EmbeddedParam(embeddedMatches.first())
                        else ->
                            Constructor.Param.RelationParam(relationMatches.first())
                    }
                    else -> {
                        context.logger.e(param, ProcessorErrors.ambigiousConstructor(
                                pojo = element.qualifiedName.toString(),
                                paramName = paramName,
                                matchingFields = matchingFields.map { it.getPath() } +
                                        embeddedMatches.map { it.field.getPath() } +
                                        relationMatches.map { it.field.getPath() }
                        ))
                        null
                    }
                }
            }
            if (params.any { it == null }) {
                failedConstructors.add(FailedConstructor(constructor, parameterNames, params))
                null
            } else {
                @Suppress("UNCHECKED_CAST")
                Constructor(constructor, params as List<Constructor.Param>)
            }
        }.filterNotNull()
        when {
            goodConstructors.isEmpty() -> {
                if (failedConstructors.isNotEmpty()) {
                    val failureMsg = failedConstructors.joinToString("\n") { entry ->
                        entry.log()
                    }
                    context.logger.e(element, ProcessorErrors.MISSING_POJO_CONSTRUCTOR +
                            "\nTried the following constructors but they failed to match:" +
                            "\n$failureMsg")
                }
                context.logger.e(element, ProcessorErrors.MISSING_POJO_CONSTRUCTOR)
                return null
            }
            goodConstructors.size > 1 -> {
                // if the Pojo is a Kotlin data class then pick its primary constructor. This is
                // better than picking the no-arg constructor and forcing users to define fields as
                // vars.
                val primaryConstructor =
                    kotlinMetadata?.findPrimaryConstructorSignature()?.let { signature ->
                        goodConstructors.firstOrNull {
                            it.element.descriptor(context.processingEnv.typeUtils) == signature
                    }
                }
                if (primaryConstructor != null) {
                    return primaryConstructor
                }
                // if there is a no-arg constructor, pick it. Even though it is weird, easily happens
                // with kotlin data classes.
                val noArg = goodConstructors.firstOrNull { it.params.isEmpty() }
                if (noArg != null) {
                    context.logger.w(Warning.DEFAULT_CONSTRUCTOR, element,
                            ProcessorErrors.TOO_MANY_POJO_CONSTRUCTORS_CHOOSING_NO_ARG)
                    return noArg
                }
                goodConstructors.forEach {
                    context.logger.e(it.element, ProcessorErrors.TOO_MANY_POJO_CONSTRUCTORS)
                }
                return null
            }
            else -> return goodConstructors.first()
        }
    }

    private fun processEmbeddedField(
        declaredType: DeclaredType?,
        variableElement: VariableElement
    ): EmbeddedField? {
        val asMemberType = MoreTypes.asMemberOf(
            context.processingEnv.typeUtils, declaredType, variableElement)
        val asTypeElement = asMemberType.asTypeElement()

        if (detectReferenceRecursion(asTypeElement)) {
            return null
        }

        val fieldPrefix = variableElement.toAnnotationBox(Embedded::class)?.value?.prefix ?: ""
        val inheritedPrefix = parent?.prefix ?: ""
        val embeddedField = Field(
                variableElement,
                variableElement.simpleName.toString(),
                type = asMemberType,
                affinity = null,
                parent = parent)
        val subParent = EmbeddedField(
                field = embeddedField,
                prefix = inheritedPrefix + fieldPrefix,
                parent = parent)
        subParent.pojo = PojoProcessor.createFor(
                context = context.fork(variableElement),
                element = asTypeElement,
                bindingScope = bindingScope,
                parent = subParent,
                referenceStack = referenceStack).process()
        return subParent
    }

    private fun processRelationField(
        myFields: List<Field>,
        container: DeclaredType?,
        relationElement: VariableElement
    ): androidx.room.vo.Relation? {
        val annotation = relationElement.toAnnotationBox(Relation::class)!!

        val parentField = myFields.firstOrNull {
            it.columnName == annotation.value.parentColumn
        }
        if (parentField == null) {
            context.logger.e(relationElement,
                    ProcessorErrors.relationCannotFindParentEntityField(
                            entityName = element.qualifiedName.toString(),
                            columnName = annotation.value.parentColumn,
                            availableColumns = myFields.map { it.columnName }))
            return null
        }
        // parse it as an entity.
        val asMember = MoreTypes
                .asMemberOf(context.processingEnv.typeUtils, container, relationElement)
        if (asMember.kind == TypeKind.ERROR) {
            context.logger.e(ProcessorErrors.CANNOT_FIND_TYPE, element)
            return null
        }
        val declared = MoreTypes.asDeclared(asMember)
        if (!declared.isCollection()) {
            context.logger.e(relationElement, ProcessorErrors.RELATION_NOT_COLLECTION)
            return null
        }
        val typeArg = declared.typeArguments.first().extendsBoundOrSelf()
        if (typeArg.kind == TypeKind.ERROR) {
            context.logger.e(typeArg.asTypeElement(), CANNOT_FIND_TYPE)
            return null
        }
        val typeArgElement = typeArg.asTypeElement()
        val entityClassInput = annotation.getAsTypeMirror("entity")

        // do we need to decide on the entity?
        val inferEntity = (entityClassInput == null ||
                MoreTypes.isTypeOf(Any::class.java, entityClassInput))
        val entityElement = if (inferEntity) {
            typeArgElement
        } else {
            entityClassInput!!.asTypeElement()
        }

        if (detectReferenceRecursion(entityElement)) {
            return null
        }

        val entity = EntityOrViewProcessor(context, entityElement, referenceStack).process()

        // now find the field in the entity.
        val entityField = entity.findFieldByColumnName(annotation.value.entityColumn)

        if (entityField == null) {
            context.logger.e(relationElement,
                    ProcessorErrors.relationCannotFindEntityField(
                            entityName = entity.typeName.toString(),
                            columnName = annotation.value.entityColumn,
                            availableColumns = entity.columnNames))
            return null
        }

        val field = Field(
                element = relationElement,
                name = relationElement.simpleName.toString(),
                type = context.processingEnv.typeUtils.asMemberOf(container, relationElement),
                affinity = null,
                parent = parent)

        val projection = if (annotation.value.projection.isEmpty()) {
            // we need to infer the projection from inputs.
            createRelationshipProjection(inferEntity, typeArg, entity, entityField, typeArgElement)
        } else {
            // make sure projection makes sense
            validateRelationshipProjection(annotation.value.projection, entity, relationElement)
            annotation.value.projection.asList()
        }
        // if types don't match, row adapter prints a warning
        return androidx.room.vo.Relation(
                entity = entity,
                pojoType = typeArg,
                field = field,
                parentField = parentField,
                entityField = entityField,
                projection = projection
        )
    }

    private fun validateRelationshipProjection(
        projectionInput: Array<String>,
        entity: EntityOrView,
        relationElement: VariableElement
    ) {
        val missingColumns = projectionInput.toList() - entity.columnNames
        if (missingColumns.isNotEmpty()) {
            context.logger.e(relationElement,
                    ProcessorErrors.relationBadProject(entity.typeName.toString(),
                            missingColumns, entity.columnNames))
        }
    }

    /**
     * Create the projection column list based on the relationship args.
     *
     *  if entity field in the annotation is not specified, it is the method return type
     *  if it is specified in the annotation:
     *       still check the method return type, if the same, use it
     *       if not, check to see if we can find a column Adapter, if so use the childField
     *       last resort, try to parse it as a pojo to infer it.
     */
    private fun createRelationshipProjection(
        inferEntity: Boolean,
        typeArg: TypeMirror,
        entity: EntityOrView,
        entityField: Field,
        typeArgElement: TypeElement
    ): List<String> {
        return if (inferEntity || typeArg.typeName() == entity.typeName) {
            entity.columnNames
        } else {
            val columnAdapter = context.typeAdapterStore.findCursorValueReader(typeArg, null)
            if (columnAdapter != null) {
                // nice, there is a column adapter for this, assume single column response
                listOf(entityField.name)
            } else {
                // last resort, it needs to be a pojo
                val pojo = PojoProcessor.createFor(
                        context = context,
                        element = typeArgElement,
                        bindingScope = FieldProcessor.BindingScope.READ_FROM_CURSOR,
                        parent = parent,
                        referenceStack = referenceStack).process()
                pojo.columnNames
            }
        }
    }

    private fun detectReferenceRecursion(typeElement: TypeElement): Boolean {
        if (referenceStack.contains(typeElement.qualifiedName)) {
            context.logger.e(
                    typeElement,
                    ProcessorErrors
                            .RECURSIVE_REFERENCE_DETECTED
                            .format(computeReferenceRecursionString(typeElement)))
            return true
        }
        return false
    }

    private fun computeReferenceRecursionString(typeElement: TypeElement): String {
        val recursiveTailTypeName = typeElement.qualifiedName

        val referenceRecursionList = mutableListOf<Name>()
        with(referenceRecursionList) {
            add(recursiveTailTypeName)
            addAll(referenceStack.toList().takeLastWhile { it != recursiveTailTypeName })
            add(recursiveTailTypeName)
        }

        return referenceRecursionList.joinToString(" -> ")
    }

    private fun assignGetters(fields: List<Field>, getterCandidates: List<PojoMethod>) {
        fields.forEach { field ->
            assignGetter(field, getterCandidates)
        }
    }

    private fun assignGetter(field: Field, getterCandidates: List<PojoMethod>) {
        val success = chooseAssignment(field = field,
                candidates = getterCandidates,
                nameVariations = field.getterNameWithVariations,
                getType = { method ->
                    method.resolvedType.returnType
                },
                assignFromField = {
                    field.getter = FieldGetter(
                            name = field.name,
                            type = field.type,
                            callType = CallType.FIELD)
                },
                assignFromMethod = { match ->
                    field.getter = FieldGetter(
                            name = match.name,
                            type = match.resolvedType.returnType,
                            callType = CallType.METHOD)
                },
                reportAmbiguity = { matching ->
                    context.logger.e(field.element,
                            ProcessorErrors.tooManyMatchingGetters(field, matching))
                })
        context.checker.check(success, field.element, CANNOT_FIND_GETTER_FOR_FIELD)
    }

    private fun assignSetters(
        fields: List<Field>,
        setterCandidates: List<PojoMethod>,
        constructor: Constructor?
    ) {
        fields.forEach { field ->
            assignSetter(field, setterCandidates, constructor)
        }
    }

    private fun assignSetter(
        field: Field,
        setterCandidates: List<PojoMethod>,
        constructor: Constructor?
    ) {
        if (constructor != null && constructor.hasField(field)) {
            field.setter = FieldSetter(
                    name = field.name,
                    type = field.type,
                    callType = CallType.CONSTRUCTOR
            )
            return
        }
        val success = chooseAssignment(field = field,
                candidates = setterCandidates,
                nameVariations = field.setterNameWithVariations,
                getType = { method ->
                    method.resolvedType.parameterTypes.first()
                },
                assignFromField = {
                    field.setter = FieldSetter(
                            name = field.name,
                            type = field.type,
                            callType = CallType.FIELD)
                },
                assignFromMethod = { match ->
                    val paramType = match.resolvedType.parameterTypes.first()
                    field.setter = FieldSetter(
                            name = match.name,
                            type = paramType,
                            callType = CallType.METHOD)
                },
                reportAmbiguity = { matching ->
                    context.logger.e(field.element,
                            ProcessorErrors.tooManyMatchingSetter(field, matching))
                })
        context.checker.check(success, field.element, CANNOT_FIND_SETTER_FOR_FIELD)
    }

    /**
     * Finds a setter/getter from available list of methods.
     * It returns true if assignment is successful, false otherwise.
     * At worst case, it sets to the field as if it is accessible so that the rest of the
     * compilation can continue.
     */
    private fun chooseAssignment(
        field: Field,
        candidates: List<PojoMethod>,
        nameVariations: List<String>,
        getType: (PojoMethod) -> TypeMirror,
        assignFromField: () -> Unit,
        assignFromMethod: (PojoMethod) -> Unit,
        reportAmbiguity: (List<String>) -> Unit
    ): Boolean {
        if (field.element.hasAnyOf(PUBLIC)) {
            assignFromField()
            return true
        }
        val types = context.processingEnv.typeUtils

        val matching = candidates
                .filter {
                    // b/69164099
                    types.isAssignableWithoutVariance(getType(it), field.type) &&
                            (field.nameWithVariations.contains(it.name) ||
                            nameVariations.contains(it.name))
                }
                .groupBy {
                    if (it.element.hasAnyOf(PUBLIC)) PUBLIC else PROTECTED
                }
        if (matching.isEmpty()) {
            // we always assign to avoid NPEs in the rest of the compilation.
            assignFromField()
            // if field is not private, assume it works (if we are on the same package).
            // if not, compiler will tell, we didn't have any better alternative anyways.
            return !field.element.hasAnyOf(PRIVATE)
        }
        val match = verifyAndChooseOneFrom(matching[PUBLIC], reportAmbiguity)
                ?: verifyAndChooseOneFrom(matching[PROTECTED], reportAmbiguity)
        if (match == null) {
            assignFromField()
            return false
        } else {
            assignFromMethod(match)
            return true
        }
    }

    private fun verifyAndChooseOneFrom(
        candidates: List<PojoMethod>?,
        reportAmbiguity: (List<String>) -> Unit
    ): PojoMethod? {
        if (candidates == null) {
            return null
        }
        if (candidates.size > 1) {
            reportAmbiguity(candidates.map { it.name })
        }
        return candidates.first()
    }

    interface Delegate {

        fun onPreProcess(element: TypeElement)

        fun findConstructors(element: TypeElement): List<ExecutableElement>

        fun createPojo(
            element: TypeElement,
            declaredType: DeclaredType,
            fields: List<Field>,
            embeddedFields: List<EmbeddedField>,
            relations: List<androidx.room.vo.Relation>,
            constructor: Constructor?
        ): Pojo
    }

    private class DefaultDelegate(private val context: Context) : Delegate {
        override fun onPreProcess(element: TypeElement) {
            // Check that certain Room annotations with @Target(METHOD) are not used in the POJO
            // since it is not annotated with AutoValue.
            element.getAllMethodsIncludingSupers()
                .filter { it.hasAnyOf(*TARGET_METHOD_ANNOTATIONS) }
                .forEach { method ->
                    val annotationName = TARGET_METHOD_ANNOTATIONS
                        .first { method.hasAnnotation(it) }
                        .java.simpleName
                    context.logger.e(method,
                        ProcessorErrors.invalidAnnotationTarget(annotationName, method.kind))
                }
        }

        override fun findConstructors(element: TypeElement) = ElementFilter.constructorsIn(
                element.enclosedElements).filterNot {
            it.hasAnnotation(Ignore::class) || it.hasAnyOf(PRIVATE)
        }

        override fun createPojo(
            element: TypeElement,
            declaredType: DeclaredType,
            fields: List<Field>,
            embeddedFields: List<EmbeddedField>,
            relations: List<androidx.room.vo.Relation>,
            constructor: Constructor?
        ): Pojo {
            return Pojo(
                    element = element,
                    type = declaredType,
                    fields = fields,
                    embeddedFields = embeddedFields,
                    relations = relations,
                    constructor = constructor)
        }
    }

    private data class FailedConstructor(
        val method: ExecutableElement,
        val params: List<String>,
        val matches: List<Constructor.Param?>
    ) {
        fun log(): String {
            val logPerParam = params.withIndex().joinToString(", ") {
                "param:${it.value} -> matched field:" + (matches[it.index]?.log() ?: "unmatched")
            }
            return "$method -> [$logPerParam]"
        }
    }
}
