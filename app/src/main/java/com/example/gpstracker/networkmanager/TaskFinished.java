package com.example.gpstracker.networkmanager;

interface TaskFinished<T> {
    void onTaskFinished(T data);
}
