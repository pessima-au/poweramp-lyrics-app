package com.example.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

object SafStorageHelper {
    private const val TAG = "SafStorageHelper"

    fun saveLrcUsingSaf(context: Context, treeUriStr: String, trackPath: String, lrcContent: String): Boolean {
        try {
            val treeUri = Uri.parse(treeUriStr)
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            
            // Normalize paths
            val normalizedTrackPath = File(trackPath).canonicalPath // e.g. /storage/emulated/0/Music/Artist/Album/Song.mp3
            
            // Extract relative path of the song relative to the SAF root directory name (e.g. "Music")
            val relativePath = getRelativePathFromTrack(normalizedTrackPath, rootDoc.name) ?: return false
            Log.d(TAG, "Relative path found: $relativePath")
            
            val parts = relativePath.split("/").filter { it.isNotEmpty() }
            if (parts.isEmpty()) return false
            
            var currentFolder = rootDoc
            // Navigate to the target directory (all parts except the last one which is the filename)
            for (i in 0 until parts.size - 1) {
                val folderName = parts[i]
                val nextFolder = currentFolder.findFile(folderName)
                currentFolder = if (nextFolder != null && nextFolder.isDirectory) {
                    nextFolder
                } else {
                    currentFolder.createDirectory(folderName) ?: return false
                }
            }
            
            // Now we are in the target directory, write the LRC file alongside the music file
            val songFileName = parts.last()
            val lastDot = songFileName.lastIndexOf('.')
            val lrcFileName = if (lastDot != -1) {
                songFileName.substring(0, lastDot) + ".lrc"
            } else {
                "$songFileName.lrc"
            }
            
            var lrcFile = currentFolder.findFile(lrcFileName)
            if (lrcFile == null) {
                lrcFile = currentFolder.createFile("text/plain", lrcFileName)
            }
            
            if (lrcFile != null) {
                context.contentResolver.openOutputStream(lrcFile.uri)?.use { os ->
                    os.write(lrcContent.toByteArray(Charsets.UTF_8))
                }
                Log.d(TAG, "Successfully wrote LRC file using SAF: $lrcFileName at ${lrcFile.uri}")
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving LRC via SAF: ${e.message}", e)
        }
        return false
    }

    private fun getRelativePathFromTrack(trackPath: String, rootName: String?): String? {
        // e.g. trackPath: "/storage/emulated/0/Music/Artist/Album/Song.mp3"
        if (rootName != null) {
            val index = trackPath.indexOf("/$rootName/", ignoreCase = true)
            if (index != -1) {
                return trackPath.substring(index + rootName.length + 2)
            }
        }
        
        // Fallback: try standard /storage/emulated/0/ parts
        val searchStr = "/storage/emulated/0/"
        val index = trackPath.indexOf(searchStr, ignoreCase = true)
        if (index != -1) {
            val after = trackPath.substring(index + searchStr.length)
            // Skip the first segment (which might be the root folder like Music) if we assume we are inside it
            val segments = after.split("/")
            if (segments.size > 1) {
                return segments.drop(1).joinToString("/")
            }
            return after
        }
        
        // If all else fails, just return the name of the file
        return File(trackPath).name
    }
}
