package com.example.facerecognition.customview

import com.example.facerecognition.tflite.SimilarityClassifier.Recognition

interface ResultsView {
    fun setResults(results: List<Recognition?>?)
}