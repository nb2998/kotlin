/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.decompiler.common

import org.jetbrains.kotlin.idea.decompiler.stubBuilder.AnnotationInfo
import org.jetbrains.kotlin.idea.decompiler.stubBuilder.AnnotationInfoWithTarget
import org.jetbrains.kotlin.protobuf.MessageLite
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.SerializerExtensionProtocol
import org.jetbrains.kotlin.serialization.deserialization.AnnotatedCallableKind
import org.jetbrains.kotlin.serialization.deserialization.AnnotationAndConstantLoader
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import org.jetbrains.kotlin.serialization.deserialization.ProtoContainer
import org.jetbrains.kotlin.types.KotlinType

//TODO: support loading of annotation arguments
class AnnotationLoaderForStubBuilderImpl(
        private val protocol: SerializerExtensionProtocol
) : AnnotationAndConstantLoader<AnnotationInfo, Any, AnnotationInfoWithTarget> {

    override fun loadClassAnnotations(container: ProtoContainer.Class): List<AnnotationInfo> =
         container.classProto.getExtension(protocol.classAnnotation).orEmpty().map { AnnotationInfo(container.nameResolver.getClassId(it.id), emptyMap()) }

    override fun loadCallableAnnotations(
            container: ProtoContainer,
            proto: MessageLite,
            kind: AnnotatedCallableKind
    ): List<AnnotationInfoWithTarget> {
        val annotations = when (proto) {
            is ProtoBuf.Constructor -> proto.getExtension(protocol.constructorAnnotation)
            is ProtoBuf.Function -> proto.getExtension(protocol.functionAnnotation)
            is ProtoBuf.Property -> proto.getExtension(protocol.propertyAnnotation)
            else -> error("Unknown message: $proto")
        }.orEmpty()
        return annotations.map {
            AnnotationInfoWithTarget(AnnotationInfo(container.nameResolver.getClassId(it.id), emptyMap()), null)
        }
    }

    override fun loadEnumEntryAnnotations(container: ProtoContainer, proto: ProtoBuf.EnumEntry): List<AnnotationInfo> =
            proto.getExtension(protocol.enumEntryAnnotation).orEmpty().map { AnnotationInfo(container.nameResolver.getClassId(it.id), emptyMap()) }

    override fun loadValueParameterAnnotations(
            container: ProtoContainer,
            callableProto: MessageLite,
            kind: AnnotatedCallableKind,
            parameterIndex: Int,
            proto: ProtoBuf.ValueParameter
    ): List<AnnotationInfo> =
            proto.getExtension(protocol.parameterAnnotation).orEmpty().map { AnnotationInfo(container.nameResolver.getClassId(it.id), emptyMap()) }

    override fun loadExtensionReceiverParameterAnnotations(
            container: ProtoContainer,
            proto: MessageLite,
            kind: AnnotatedCallableKind
    ): List<AnnotationInfo> = emptyList()

    override fun loadTypeAnnotations(
            proto: ProtoBuf.Type,
            nameResolver: NameResolver
    ): List<AnnotationInfo> =
            proto.getExtension(protocol.typeAnnotation).orEmpty().map { AnnotationInfo(nameResolver.getClassId(it.id), emptyMap()) }

    override fun loadTypeParameterAnnotations(proto: ProtoBuf.TypeParameter, nameResolver: NameResolver): List<AnnotationInfo> =
        proto.getExtension(protocol.typeParameterAnnotation).orEmpty().map { AnnotationInfo(nameResolver.getClassId(it.id), emptyMap()) }

    override fun loadPropertyConstant(
            container: ProtoContainer,
            proto: ProtoBuf.Property,
            expectedType: KotlinType
    ) {}
}
