package dev.agentreview.intellij.editor

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile

class ReviewPageVirtualFile : LightVirtualFile("Review", PlainTextFileType.INSTANCE, "") {
    init {
        isWritable = false
    }
}
