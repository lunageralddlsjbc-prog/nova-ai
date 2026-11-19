#include <jni.h>
#include <string>
#include <vector>
#include <thread>
#include <atomic>
#include "llama.h"

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static std::thread g_gen_thread;
static std::atomic<bool> g_stop_flag{false};
static JavaVM* g_jvm = nullptr;
static jobject g_callback = nullptr;

extern "C" {

JNIEXPORT jint JNICALL Java_com_nova_ai_NativeBridge_loadModel(JNIEnv* env, jobject thiz, jstring modelPath, jint threads, jint ctxSize) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_load_model_from_file(path, mp);
    env->ReleaseStringUTFChars(modelPath, path);
    if (!g_model) return -1;
    llama_context_params cp = llama_context_default_params();
    cp.n_ctx = ctxSize;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    g_ctx = llama_new_context_with_model(g_model, cp);
    if (!g_ctx) { llama_free_model(g_model); g_model = nullptr; return -2; }
    env->GetJavaVM(&g_jvm);
    return 0;
}

JNIEXPORT void JNICALL Java_com_nova_ai_NativeBridge_generateStream(JNIEnv* env, jobject thiz, jstring prompt, jfloat temperature, jint maxTokens, jobject callback) {
    const char* p = env->GetStringUTFChars(prompt, nullptr);
    std::string prompt_std(p);
    env->ReleaseStringUTFChars(prompt, p);
    g_callback = env->NewGlobalRef(callback);
    g_stop_flag = false;

    g_gen_thread = std::thread([prompt_std, maxTokens]() {
        JNIEnv* env;
        g_jvm->AttachCurrentThread(&env, nullptr);
        jclass cls = env->GetObjectClass(g_callback);
        jmethodID mid = env->GetMethodID(cls, "invoke", "(Ljava/lang/Object;)Ljava/lang/Object;");

        // Tokenize WITH special tokens so ChatML markers are recognized
        int need = llama_tokenize(g_model, prompt_std.c_str(), (int)prompt_std.length(), nullptr, 0, true, true);
        if (need < 0) need = -need;
        std::vector<llama_token> tokens(need);
        int n_tokens = llama_tokenize(g_model, prompt_std.c_str(), (int)prompt_std.length(), tokens.data(), (int)tokens.size(), true, true);
        if (n_tokens <= 0) {
            jstring err = env->NewStringUTF("Tokenization failed");
            env->CallObjectMethod(g_callback, mid, err);
            env->DeleteGlobalRef(g_callback); g_callback = nullptr;
            g_jvm->DetachCurrentThread(); return;
        }

        int n_past = 0;
        const int B = 512;
        for (int i = 0; i < n_tokens; i += B) {
            int cur = std::min(B, n_tokens - i);
            llama_batch batch = llama_batch_init(cur, 0, 1);
            for (int j = 0; j < cur; j++) {
                batch.token[j] = tokens[i + j];
                batch.pos[j] = n_past + j;
                batch.n_seq_id[j] = 1;
                batch.seq_id[j][0] = 0;
                batch.logits[j] = (i + j == n_tokens - 1); // only LAST prompt token needs logits
            }
            batch.n_tokens = cur;
            if (llama_decode(g_ctx, batch) != 0) { llama_batch_free(batch); break; }
            llama_batch_free(batch);
            n_past += cur;
        }

        const int n_vocab = llama_n_vocab(g_model);
        const llama_token eos = llama_token_eos(g_model);
        std::vector<llama_token_data> cand(n_vocab);

        for (int i = 0; i < maxTokens && !g_stop_flag; i++) {
            const float* logits = llama_get_logits(g_ctx);
            if (!logits) break;
            // manual greedy argmax (no sampler API needed)
            int best = 0; float bestv = logits[0];
            for (int v = 1; v < n_vocab; v++) { if (logits[v] > bestv) { bestv = logits[v]; best = v; } }
            llama_token new_token = (llama_token)best;
            if (new_token == eos) break;

            char buf[64];
            int len = llama_token_to_piece(g_model, new_token, buf, sizeof(buf) - 1, 0, true);
            if (len < 0) len = 0;
            buf[len] = '\0';
            jstring jtoken = env->NewStringUTF(buf);
            env->CallObjectMethod(g_callback, mid, jtoken);
            env->DeleteLocalRef(jtoken);

            llama_batch batch = llama_batch_init(1, 0, 1);
            batch.token[0] = new_token;
            batch.pos[0] = n_past;
            batch.n_seq_id[0] = 1;
            batch.seq_id[0][0] = 0;
            batch.logits[0] = true;
            batch.n_tokens = 1;
            if (llama_decode(g_ctx, batch) != 0) { llama_batch_free(batch); break; }
            llama_batch_free(batch);
            n_past++;
        }

        env->DeleteGlobalRef(g_callback);
        g_callback = nullptr;
        g_jvm->DetachCurrentThread();
    });
    g_gen_thread.detach();
}

JNIEXPORT void JNICALL Java_com_nova_ai_NativeBridge_stopGeneration(JNIEnv* env, jobject thiz) { g_stop_flag = true; }
JNIEXPORT void JNICALL Java_com_nova_ai_NativeBridge_unloadModel(JNIEnv* env, jobject thiz) {
    g_stop_flag = true;
    if (g_gen_thread.joinable()) g_gen_thread.join();
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    llama_backend_free();
}
JNIEXPORT jint JNICALL Java_com_nova_ai_NativeBridge_add(JNIEnv* env, jobject thiz, jint a, jint b) { return a + b; }
}
