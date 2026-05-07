package dev.fatihdogmus.localreview.editor

import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.testFramework.LightVirtualFile

class ReviewPageVirtualFile : LightVirtualFile("Local Review", PlainTextFileType.INSTANCE, "") {
    init {
        isWritable = false
    }
}
