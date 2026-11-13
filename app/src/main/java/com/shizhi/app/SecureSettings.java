package com.shizhi.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureSettings {
    private final SharedPreferences prefs;
    private static final String ALIAS = "shizhi.settings.v1";
    public SecureSettings(Context c) { prefs = c.getSharedPreferences("vault", Context.MODE_PRIVATE); }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return ((KeyStore.SecretKeyEntry)store.getEntry(ALIAS, null)).getSecretKey();
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return gen.generateKey();
    }
    public synchronized JSONObject read() {
        try {
            String blob = prefs.getString("encrypted", ""); if (blob.isEmpty()) return defaults();
            String[] p = blob.split(":"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(p[0], Base64.NO_WRAP)));
            JSONObject result = defaults(), saved = new JSONObject(new String(cipher.doFinal(Base64.decode(p[1], Base64.NO_WRAP)), StandardCharsets.UTF_8));
            for (java.util.Iterator<String> i = saved.keys(); i.hasNext();) { String k = i.next(); if(!k.equals("requestLimit"))result.put(k, saved.get(k)); }
            return result;
        } catch (Exception e) { throw new IllegalStateException("无法解密本机设置。请清除应用数据后重新配置（会删除历史）。", e); }
    }
    public synchronized void write(JSONObject value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String blob = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString("encrypted", blob).commit()) throw new IllegalStateException("设置保存失败");
    }
    public synchronized JSONObject publicView() throws Exception {
        JSONObject j = read(); j.put("hasKey", !j.optString("apiKey").isEmpty()); j.put("loggedIn", !j.optString("cookie").isEmpty());
        j.remove("apiKey"); j.remove("cookie"); return j;
    }
    public synchronized void saveApi(JSONObject input) throws Exception {
        JSONObject j = read(); String url = ApiEndpoint.normalize(input.optString("baseUrl"));
        String model = input.optString("model").trim(); if (model.isEmpty() || model.length()>120) throw new IllegalArgumentException("请填写有效模型名");
        if(!url.equals(j.optString("baseUrl")))j.remove("consentUrl"); j.put("baseUrl", url); j.put("model", model);
        String param=input.optString("tokenParam","max_tokens"); if(!param.equals("max_tokens")&&!param.equals("max_completion_tokens"))throw new IllegalArgumentException("无效输出长度参数"); j.put("tokenParam",param);
        String k = input.optString("apiKey").trim(); if (!k.isEmpty()) { if(k.contains("\n") || k.contains("\r")) throw new IllegalArgumentException("Key 格式无效"); j.put("apiKey", k); }
        if (input.optBoolean("clearKey")) j.put("apiKey", ""); write(j);
    }
    public synchronized void saveFeature(JSONObject input) throws Exception {
        int n = input.optInt("pendingLimit", 3);
        new QueuePolicy(n);
        double rate = input.optDouble("speechRate", 1.0); int chars = input.optInt("inputChars", 12000), score = input.optInt("minScore", 65);
        if(rate<0.7 || rate>1.6 || chars<3000 || chars>30000 || score<30 || score>95) throw new IllegalArgumentException("设置超出允许范围");
        JSONObject j = read();
        j.put("pendingLimit", n).put("speechRate", rate).put("inputChars", chars).put("minScore", score)
         .put("autoPlay", input.optBoolean("autoPlay", true)).put("interests", input.optString("interests").trim()).put("blocked", input.optString("blocked").trim()); write(j);
    }
    public synchronized void setLogin(String cookie, String username) throws Exception { JSONObject j = read(); j.put("cookie",cookie).put("username", username); write(j); }
    public static JSONObject defaults() {
        try { return new JSONObject().put("baseUrl", "https://api.openai.com/v1").put("apiKey", "").put("model", "").put("tokenParam","max_tokens").put("cookie", "").put("username", "")
            .put("pendingLimit",3).put("interests", "").put("blocked", "").put("speechRate",1.0).put("autoPlay",true).put("inputChars",12000).put("minScore",65); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
}