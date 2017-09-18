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

package org.jetbrains.kotlin.psi.stubs.elements

import com.intellij.openapi.project.Project
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.util.io.StringRef
import org.jetbrains.annotations.NonNls
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.plainContent
import org.jetbrains.kotlin.psi.stubs.KotlinAnnotationEntryStub
import org.jetbrains.kotlin.psi.stubs.impl.KotlinAnnotationEntryStubImpl

class KtAnnotationEntryElementType(
        @NonNls debugName: String
) : KtStubElementType<KotlinAnnotationEntryStub, KtAnnotationEntry>(debugName, KtAnnotationEntry::class.java, KotlinAnnotationEntryStub::class.java) {

    override fun createStub(psi: KtAnnotationEntry, parentStub: StubElement<*>): KotlinAnnotationEntryStub {
        val shortName = KtPsiUtil.getShortName(psi)
        val resultName = shortName?.asString() ?: psi.text
        val valueArgumentList = psi.valueArgumentList
        val hasValueArguments = valueArgumentList != null && !valueArgumentList.arguments.isEmpty()
        val patternNames = psi.replacementForPatternNames()
        return KotlinAnnotationEntryStubImpl(parentStub, resultName, hasValueArguments, patternNames)
    }

    override fun serialize(stub: KotlinAnnotationEntryStub, dataStream: StubOutputStream) {
        dataStream.writeName(stub.shortName)
        dataStream.writeBoolean(stub.hasValueArguments)

        dataStream.writeVarInt(stub.replacementForPatternNames.size)
        stub.replacementForPatternNames.forEach { dataStream.writeName(it) }
    }

    override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>): KotlinAnnotationEntryStub {
        val shortName = dataStream.readName()!!.string
        val hasValueArguments = dataStream.readBoolean()
        val count = dataStream.readVarInt()
        val patternNames = if (count > 0) {
            ArrayList<String>(count).apply {
                repeat(count) { add(dataStream.readName()!!.string) }
            }
        }
        else {
            emptyList<String>()
        }
        return KotlinAnnotationEntryStubImpl(parentStub, shortName, hasValueArguments, patternNames)
    }

    override fun indexStub(stub: KotlinAnnotationEntryStub, sink: IndexSink) {
        StubIndexService.getInstance().indexAnnotation(stub, sink)
    }

    private fun KtAnnotationEntry.replacementForPatternNames(): Collection<String> {
        val referencedName = (calleeExpression?.typeReference?.typeElement as? KtUserType)?.referencedName ?: return emptyList()
        if (referencedName != "ReplacementFor") return emptyList() //TODO: import aliases
        return extractExpressionsFromReplacementForAnnotation(this)
                .mapNotNull { it as? KtStringTemplateExpression }
                .mapNotNull { replacementForPatternName(it.plainContent, project) }
                .distinct()
    }
}

fun extractExpressionsFromReplacementForAnnotation(entry: KtAnnotationEntry): Collection<KtExpression> {
    //TODO: named argument and "*" argument
    return entry.valueArguments
            .takeWhile { !it.isNamed() }
            .mapNotNull { it.getArgumentExpression() }
}

fun replacementForPatternName(pattern: String, project: Project): String? {
    val expression = try {
        //TODO: escape entries
        KtPsiFactory(project).createExpression(pattern)
    }
    catch (t: Throwable) {
        return null
    }
    //TODO: we should highlight patterns that are currently not supported
    return expression.callNameExpression()?.getReferencedName()
}

private fun KtExpression.callNameExpression(): KtNameReferenceExpression? {
    return when(this) {
        is KtNameReferenceExpression -> this
        is KtCallExpression -> calleeExpression as? KtNameReferenceExpression
        is KtQualifiedExpression -> selectorExpression?.callNameExpression()
        else -> null
    }
}
