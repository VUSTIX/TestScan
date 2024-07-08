package com.example.server.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface FileDao {
    @Query("SELECT * FROM fileentity")
    fun getAll(): List<FileEntity>

    @Query("SELECT txt_file_path FROM fileentity")
    fun getAllTxtFilePaths(): List<String>

    @Insert
    fun insertAll(vararg files: FileEntity)

    @Insert
    fun insert(file: FileEntity): Long

    @Update
    fun update(file: FileEntity)
}