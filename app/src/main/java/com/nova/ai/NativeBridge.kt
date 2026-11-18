package com.nova.ai
object NativeBridge {
    init { System.loadLibrary("nova_native") }
    external fun loadModel(modelPath: String, threads: Int, ctxSize: Int): Int
    external fun generateStream(prompt: String, temperature: Float, maxTokens: Int, onToken: (String) -> Unit)
    external fun stopGeneration()
    external fun unloadModel()
    external fun add(a: Int, b: Int): Int
}
