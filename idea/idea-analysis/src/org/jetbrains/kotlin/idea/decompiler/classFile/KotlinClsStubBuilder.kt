/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.decompiler.classFile

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.compiled.ClsStubBuilder
import com.intellij.psi.impl.compiled.ClassFileStubBuilder
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.util.indexing.FileContent
import org.jetbrains.kotlin.descriptors.SourceElement
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.idea.caches.IDEKotlinBinaryClassCache
import org.jetbrains.kotlin.idea.decompiler.stubBuilder.*
import org.jetbrains.kotlin.load.java.components.DescriptorResolverUtils
import org.jetbrains.kotlin.load.kotlin.*
import org.jetbrains.kotlin.load.kotlin.header.KotlinClassHeader
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.stubs.KotlinStubVersions
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.serialization.ProtoBuf
import org.jetbrains.kotlin.serialization.deserialization.NameResolver
import org.jetbrains.kotlin.serialization.deserialization.TypeTable
import org.jetbrains.kotlin.serialization.jvm.JvmProtoBufUtil
import org.jetbrains.kotlin.storage.LockBasedStorageManager
import org.jetbrains.kotlin.utils.compact
import java.util.*

open class KotlinClsStubBuilder : ClsStubBuilder() {
    override fun getStubVersion() = ClassFileStubBuilder.STUB_VERSION + KotlinStubVersions.CLASSFILE_STUB_VERSION

    override fun buildFileStub(content: FileContent): PsiFileStub<*>? {
        val file = content.file

        if (isKotlinInternalCompiledFile(file, content.content)) {
            return null
        }

        return doBuildFileStub(file, content.content)
    }

    private fun doBuildFileStub(file: VirtualFile, fileContent: ByteArray): PsiFileStub<KtFile>? {
        val kotlinClass = IDEKotlinBinaryClassCache.getKotlinBinaryClass(file, fileContent) ?: error("Can't find binary class for Kotlin file: $file")
        val header = kotlinClass.classHeader
        val classId = kotlinClass.classId
        val packageFqName = classId.packageFqName
        if (!header.metadataVersion.isCompatible()) {
            return createIncompatibleAbiVersionFileStub()
        }

        val components = createStubBuilderComponents(file, packageFqName, fileContent)
        if (header.kind == KotlinClassHeader.Kind.MULTIFILE_CLASS) {
            val partFiles = findMultifileClassParts(file, classId, header)
            return createMultifileClassStub(header, partFiles, classId.asSingleFqName(), components)
        }

        val annotationData = header.data
        if (annotationData == null) {
            LOG.error("Corrupted kotlin header for file ${file.name}")
            return null
        }
        val strings = header.strings
        if (strings == null) {
            LOG.error("String table not found in file ${file.name}")
            return null
        }
        return when (header.kind) {
            KotlinClassHeader.Kind.CLASS -> {
                if (classId.isLocal) return null
                val (nameResolver, classProto) = JvmProtoBufUtil.readClassDataFrom(annotationData, strings)
                val context = components.createContext(nameResolver, packageFqName, TypeTable(classProto.typeTable))
                createTopLevelClassStub(classId, classProto, KotlinJvmBinarySourceElement(kotlinClass), context, header.isScript)
            }
            KotlinClassHeader.Kind.FILE_FACADE -> {
                val (nameResolver, packageProto) = JvmProtoBufUtil.readPackageDataFrom(annotationData, strings)
                val context = components.createContext(nameResolver, packageFqName, TypeTable(packageProto.typeTable))
                createFileFacadeStub(packageProto, classId.asSingleFqName(), context)
            }
            else -> throw IllegalStateException("Should have processed " + file.path + " with header $header")
        }
    }

    private fun createStubBuilderComponents(file: VirtualFile, packageFqName: FqName, fileContent: ByteArray): ClsStubBuilderComponents {
        val classFinder = DirectoryBasedClassFinder(file.parent!!, packageFqName)
        val classDataFinder = DirectoryBasedDataFinder(classFinder, LOG)
        val annotationLoader = AnnotationLoaderForClassFileStubBuilder(classFinder, file, fileContent)
        return ClsStubBuilderComponents(classDataFinder, annotationLoader, file)
    }

    companion object {
        val LOG = Logger.getInstance(KotlinClsStubBuilder::class.java)
    }
}

class AnnotationLoaderForClassFileStubBuilder(
        kotlinClassFinder: KotlinClassFinder,
        private val cachedFile: VirtualFile,
        private val cachedFileContent: ByteArray
) : AbstractBinaryClassAnnotationAndConstantLoader<AnnotationInfo, Any, AnnotationInfoWithTarget>(LockBasedStorageManager.NO_LOCKS, kotlinClassFinder) {

    override fun getCachedFileContent(kotlinClass: KotlinJvmBinaryClass): ByteArray? {
        if ((kotlinClass as? VirtualFileKotlinClass)?.file == cachedFile) {
            return cachedFileContent
        }
        return null
    }

    override fun loadTypeAnnotation(proto: ProtoBuf.Annotation, nameResolver: NameResolver): AnnotationInfo =
            AnnotationInfo(nameResolver.getClassId(proto.id), emptyMap()/*TODO: not supported currently*/)

    override fun loadConstant(desc: String, initializer: Any) = initializer

    override fun loadAnnotation(
            annotationClassId: ClassId,
            source: SourceElement,
            result: MutableList<AnnotationInfo>
    ): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
        return object : KotlinJvmBinaryClass.AnnotationArgumentVisitor {
            private val arguments = HashMap<Name, Any?>()

            override fun visit(name: Name?, value: Any?) {
                if (name != null) {
                    arguments[name] = value
                }
            }

            override fun visitEnum(name: Name, enumClassId: ClassId, enumEntryName: Name) {
                //TODO: not supported currently
            }

            override fun visitArray(name: Name): KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor? {
                return object : KotlinJvmBinaryClass.AnnotationArrayArgumentVisitor {
                    private val elements = ArrayList<Any?>()

                    override fun visit(value: Any?) {
                        elements.add(value)
                    }

                    override fun visitEnum(enumClassId: ClassId, enumEntryName: Name) {
                        //TODO: not supported currently
                    }

                    override fun visitEnd() {
                        arguments[name] = elements.toArray()
                    }
                }
            }

            override fun visitAnnotation(name: Name, classId: ClassId): KotlinJvmBinaryClass.AnnotationArgumentVisitor? {
                return null //TODO: not supported currently
            }
            
            override fun visitEnd() {
                result.add(AnnotationInfo(annotationClassId, arguments))
            }
        }
    }

    override fun loadPropertyAnnotations(
            propertyAnnotations: List<AnnotationInfo>,
            fieldAnnotations: List<AnnotationInfo>,
            fieldUseSiteTarget: AnnotationUseSiteTarget
    ): List<AnnotationInfoWithTarget> {
        return propertyAnnotations.map { AnnotationInfoWithTarget(it, null) } +
               fieldAnnotations.map { AnnotationInfoWithTarget(it, fieldUseSiteTarget ) }
    }

    override fun transformAnnotations(annotations: List<AnnotationInfo>): List<AnnotationInfoWithTarget> =
            annotations.map { AnnotationInfoWithTarget(it, null) }
}
