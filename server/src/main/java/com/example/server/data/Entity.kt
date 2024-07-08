package com.example.server.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class FileEntity(
    @PrimaryKey(autoGenerate = true) val id: Int,
    @ColumnInfo(name = "txt_file_path") val txtFilePath: String,
    @ColumnInfo(name = "archive_path") val archivePath: String
)