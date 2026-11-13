package com.shizhi.app;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class CoreTests {
    static int passed;
    interface Test{void run()throws Exception;}
    static void test(String name,Test t)throws Exception{t.run();passed++;System.out.println("PASS "+name);}
    static void ok(boolean condition){if(!condition)throw new AssertionError();}
    static void fails(Test task)throws Exception{boolean failed=false;try{task.run();}catch(Exception expected){failed=true;}ok(failed);}
    static JSONObject config()throws Exception{return new JSONObject().put("baseUrl","https://model.example/v1").put("apiKey","fake-test-key").put("model","test-model").put("interests","学习与技术");}
    static JSONArray subtitleLines()throws Exception{return new JSONArray().put(new JSONObject().put("from",10).put("to",20).put("content","先把输入输出写清楚，这是完整闭环中需要记录的重要信息，不应该只做空泛的描述。")).put(new JSONObject().put("from",30).put("to",40).put("content","再用具体例子验证结果，检查边界条件和失败行为，确认系统的实际结果与期望一致。")).put(new JSONObject().put("from",50).put("to",65).put("content","最后保留原始证据，不能根据不存在的信息编造结论，测试失败时应明确说明原因。"));}
    static JSONObject summary()throws Exception{return new JSONObject().put("useful",true).put("sourceMatches",true).put("source",new JSONObject().put("bvid","BV1234567890").put("cid",10)).put("summary","先明确输入输出，再通过例子验证。").put("points",new JSONArray().put(new JSONObject().put("text","明确输入输出。").put("at",10).put("quote","先把输入输出写清楚")).put(new JSONObject().put("text","通过例子验证结果。").put("at",30).put("quote","再用具体例子验证结果")));}
    static JSONObject completion(JSONObject value)throws Exception{return new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("finish_reason","stop").put("message",new JSONObject().put("content",value.toString()))));}
    static JSONObject video()throws Exception{return new JSONObject().put("bvid","BV1234567890").put("aid",100).put("cid",10).put("title","测试视频").put("author","测试作者").put("duration",100);}
    static BiliClient.Transcript transcript()throws Exception{return new BiliClient.Transcript(video(),subtitleLines(),"字幕资料",true,"中文",true,"i0.hdslb.com","10000","https://i0.hdslb.com/test.json",subtitleLines().toString());}
    static class FakeConnection extends HttpURLConnection {
        final Proxy proxy;final ByteArrayOutputStream written=new ByteArrayOutputStream();boolean disconnected;
        FakeConnection(URL u,Proxy p){super(u);proxy=p;connections.add(this);}
        public String url(){return url.toString();}
        public void connect(){}public void disconnect(){disconnected=true;}public boolean usingProxy(){return proxy.type()!=Proxy.Type.DIRECT;}
        public OutputStream getOutputStream(){return written;}
        public int getResponseCode()throws IOException{if(failDirect&&proxy.type()==Proxy.Type.DIRECT)throw new IOException("synthetic connection failure");return redirects.containsKey(url.toString())?302:statuses.getOrDefault(url.toString(),status);}
        public String getHeaderField(String name){return "Location".equalsIgnoreCase(name)?redirects.get(url.toString()):null;}
        public InputStream getInputStream()throws IOException{return new ByteArrayInputStream(response(url).getBytes(StandardCharsets.UTF_8));}
    }
    static final List<FakeConnection> connections=new ArrayList<>();static boolean failDirect;static int status=200;static JSONArray trackOverride;static String playerBvid="";static JSONObject detailOverride,playerOverride;static boolean crossVideos;
    static final Map<String,String> redirects=new HashMap<>();static final Map<String,Integer> statuses=new HashMap<>();
    static String query(URL url,String name){for(String pair:url.getQuery().split("&")){String[] parts=pair.split("=",2);if(parts.length==2&&parts[0].equals(name))return parts[1];}return "";}
    static JSONObject detailFixture(String bv)throws Exception {boolean b=bv.equals("BV9999999999");return new JSONObject().put("bvid",bv).put("aid",b?200:100).put("cid",b?20:10).put("title",b?"舞蹈视频B":"测试视频")
        .put("owner",new JSONObject().put("name",b?"作者B":"测试作者")).put("duration",100).put("pages",new JSONArray().put(new JSONObject().put("page",1).put("cid",b?20:10)).put(new JSONObject().put("page",2).put("cid",b?21:11)));}
    static JSONArray linesB()throws Exception{return new JSONArray().put(new JSONObject().put("from",1).put("to",10).put("content","这是被筛掉的视频B的舞蹈内容，和输入输出系统验证没有关系。".repeat(6)));}
    static String response(URL url)throws IOException{
        try{String path=url.getPath();JSONObject value;
            if(path.endsWith("/nav"))value=new JSONObject().put("isLogin",true).put("uname","测试账号");
            else if(path.endsWith("/rcmd"))value=new JSONObject().put("item",new JSONArray().put(new JSONObject().put("bvid","BV1234567890").put("goto","av").put("title","测试视频").put("duration",100).put("cid",10).put("owner",new JSONObject().put("name","测试作者"))));
            else if(path.endsWith("/view"))value=detailOverride==null?detailFixture(query(url,"bvid")):detailOverride;
            else if(path.endsWith("/v2")){String bv=query(url,"bvid");boolean other=bv.equals("BV9999999999");value=playerOverride==null?new JSONObject().put("bvid",playerBvid.isEmpty()?bv:playerBvid).put("aid",other?200:100).put("cid",other?20:10).put("login_mid",123).put("subtitle",new JSONObject().put("subtitles",trackOverride==null?new JSONArray().put(new JSONObject().put("id_str",other?"20000":"10000").put("lan","zh-CN").put("lan_doc","中文").put("subtitle_url","https://i0.hdslb.com/"+(crossVideos?(other?"subtitle-B.json":"subtitle-A.json"):"subtitle-test.json"))):trackOverride)):playerOverride;}
            else if(path.endsWith("subtitle-A.json"))return new JSONObject().put("body",subtitleLines()).toString();
            else if(path.endsWith("subtitle-B.json"))return new JSONObject().put("body",linesB()).toString();
            else if(path.endsWith("subtitle-test.json"))return new JSONObject().put("body",subtitleLines()).toString();
            else return "{}";
            if(path.endsWith("/rcmd")&&crossVideos)value.getJSONArray("item").put(0,new JSONObject().put("bvid","BV9999999999").put("goto","av").put("title","舞蹈视频B").put("duration",100).put("cid",20).put("owner",new JSONObject().put("name","作者B"))).put(new JSONObject().put("bvid","BV1234567890").put("goto","av").put("title","测试视频").put("duration",100).put("cid",10).put("owner",new JSONObject().put("name","测试作者")));
            return new JSONObject().put("code",0).put("data",value).toString();
        }catch(Exception e){throw new IOException(e);}
    }
    public static void main(String[] args)throws Exception{
        test("capacity defaults and rejects invalid limits",()->{QueuePolicy p=new QueuePolicy(3);ok(p.pendingLimit()==3);fails(()->p.configure(0));fails(()->p.configure(11));});
        test("full queue blocks every reservation",()->{QueuePolicy p=new QueuePolicy(3);long s=p.start();for(int i=0;i<1000;i++)ok(!p.reserve(s,3));ok(p.reserved()==0);});
        test("in-flight reservation counts toward capacity",()->{QueuePolicy p=new QueuePolicy(3);long s=p.start();ok(p.reserve(s,2));ok(!p.reserve(s,2));ok(p.complete(s,2));ok(p.reserved()==0);});
        test("no request quota during continued consumption",()->{QueuePolicy p=new QueuePolicy(3);long s=p.start();for(int i=0;i<10000;i++){ok(p.reserve(s,2));ok(p.complete(s,2));}ok(p.active(s));});
        test("stop rejects late results and stale sessions",()->{QueuePolicy p=new QueuePolicy(3);long s=p.start();ok(p.reserve(s,2));p.stop();ok(!p.complete(s,2));long next=p.start();ok(!p.reserve(s,0));ok(p.reserve(next,2));p.release(s);ok(p.reserved()==1);p.release(next);ok(p.reserved()==0);});
        test("lower limit preserves backlog and blocks new work",()->{QueuePolicy p=new QueuePolicy(5);long s=p.start();p.configure(3);ok(!p.reserve(s,5));ok(!p.reserve(s,3));ok(p.reserve(s,2));});
        test("concurrent checks cannot overbook a slot",()->{QueuePolicy p=new QueuePolicy(3);long s=p.start();ExecutorService pool=Executors.newFixedThreadPool(16);AtomicInteger n=new AtomicInteger();List<Future<?>> jobs=new ArrayList<>();for(int i=0;i<100;i++)jobs.add(pool.submit(()->{if(p.reserve(s,2))n.incrementAndGet();}));for(Future<?> f:jobs)f.get();pool.shutdown();ok(n.get()==1);});
        test("URL normalization preserves custom provider path",()->{ok(ApiEndpoint.normalize(" https://model.example/custom/v2/ ").equals("https://model.example/custom/v2"));ok(ApiEndpoint.normalize("https://model.example/v1/chat/completions/").equals("https://model.example/v1"));ok(ApiEndpoint.normalize("https://model.example").equals("https://model.example"));});
        test("URL rejects insecure and credential-bearing endpoints",()->{for(String s:new String[]{"http://model.example/v1","https://u:p@model.example","https://model.example/?key=x","https://model.example/#x","file:///tmp/x"})fails(()->ApiEndpoint.normalize(s));});
        test("seven days retention honors favorites and active audio",()->{long now=10*RetentionPolicy.SEVEN_DAYS;ok(RetentionPolicy.expires(0,now,false,false));ok(!RetentionPolicy.expires(0,now,true,false));ok(!RetentionPolicy.expires(0,now,false,true));ok(!RetentionPolicy.expires(now-RetentionPolicy.SEVEN_DAYS,now,false,false));});
        test("speech chunks preserve text and surrogate pairs",()->{StringBuilder b=new StringBuilder();for(int i=0;i<100;i++)b.append("这是一段需要完整保留的测试文本。😀");String text=b.toString();ArrayList<String> chunks=SpeechChunks.split(text);ok(String.join("",chunks).equals(text));for(String s:chunks){ok(s.length()<=160);ok(!Character.isHighSurrogate(s.charAt(s.length()-1)));ok(!Character.isLowSurrogate(s.charAt(0)));}});
        test("model parser accepts fenced JSON without forcing vendor schema",()->{AtomicInteger calls=new AtomicInteger();LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->{calls.incrementAndGet();ok(u.equals("https://model.example/v1/chat/completions"));ok(b.getInt("max_tokens")==128);ok(!b.has("response_format"));return completion(new JSONObject().put("ok",true)).toString();});ok(c.chat("s","u",128,new Net.Cancel()).getBoolean("ok"));ok(!c.usageKnown());ok(calls.get()==1);});
        test("model supports alternate token parameter and reported usage",()->{JSONObject cfg=config().put("tokenParam","max_completion_tokens");LlmClient c=new LlmClient(cfg,(u,b,cookie,key,cancel)->{ok(b.has("max_completion_tokens")&&!b.has("max_tokens"));return completion(new JSONObject().put("ok",true)).put("usage",new JSONObject().put("total_tokens",42)).toString();});c.chat("s","u",128,new Net.Cancel());ok(c.usageKnown()&&c.tokens()==42);});
        test("model POST is not retried on failure",()->{AtomicInteger n=new AtomicInteger();LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->{n.incrementAndGet();throw new IOException("synthetic");});fails(()->c.chat("s","u",128,new Net.Cancel()));ok(n.get()==1);});
        test("valid summary retains evidence and partial scope",()->{LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->completion(summary()).toString());JSONObject out=c.summarize(video(),transcript(),new Net.Cancel());ok(out.getJSONArray("points").length()==2);ok(out.getString("scope").contains("节选"));ok(out.getString("speech").contains("第一分P"));});
        test("fabricated quote is rejected",()->{JSONObject fake=summary();fake.getJSONArray("points").getJSONObject(0).put("quote","字幕里根本没有这句话");LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->completion(fake).toString());fails(()->c.summarize(video(),transcript(),new Net.Cancel()));});
        test("wrong timestamp is rejected",()->{JSONObject fake=summary();fake.getJSONArray("points").getJSONObject(0).put("at",999);LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->completion(fake).toString());fails(()->c.summarize(video(),transcript(),new Net.Cancel()));});
        test("low-value subtitles return no queued summary",()->{LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->completion(new JSONObject().put("useful",false)).toString());ok(c.summarize(video(),transcript(),new Net.Cancel())==null);});
        test("ranking cannot invent or duplicate video IDs",()->{JSONObject rank=new JSONObject().put("videos",new JSONArray().put(new JSONObject().put("bvid","BVnotallowed").put("score",100)).put(new JSONObject().put("bvid","BV1234567890").put("score",90)).put(new JSONObject().put("bvid","BV1234567890").put("score",90)));LlmClient c=new LlmClient(config(),(u,b,cookie,key,cancel)->completion(rank).toString());ok(c.rank(new JSONArray().put(video()),new Net.Cancel()).size()==1);});
        test("login cookie merge handles API/www/passport and preserves signed values",()->{String merged=BiliLoginCookies.merge(null,"DedeUserID=123; SESSDATA=a%2Fb==","SESSDATA=older; bili_jct=test");ok(merged.equals("DedeUserID=123; SESSDATA=a%2Fb==; bili_jct=test"));ok(BiliLoginCookies.fingerprint(merged).length()==64);});
        test("login detection requires an actual nonempty session cookie",()->{for(String value:new String[]{"","NOTSESSDATA=abc","SESSDATA=","SESSDATA=deleted","SESSDATA=null","SESSDATA=\"\""})ok(BiliLoginCookies.fingerprint(value).isEmpty());});
        test("non-auth cookie changes do not repeat automatic login verification",()->{String a=BiliLoginCookies.fingerprint("SESSDATA=session; DedeUserID=1; buvid3=a"),b=BiliLoginCookies.fingerprint("buvid3=b; DedeUserID=1; SESSDATA=session");ok(a.equals(b));LoginAttemptGate gate=new LoginAttemptGate();long t=gate.begin(a,false);ok(t>0);ok(gate.finish(t));for(int i=0;i<1000;i++)ok(gate.begin(b,false)==0);ok(gate.begin(b,true)>0);});
        test("login gate prevents duplicate taps and rejects stale callbacks",()->{LoginAttemptGate gate=new LoginAttemptGate();ok(gate.begin("",false)==0);long a=gate.begin("session-a",false);ok(a>0&&gate.begin("session-b",true)==0);ok(gate.finish(a));long b=gate.begin("session-b",false);ok(b>a&&!gate.current(a)&&!gate.finish(a));ok(gate.current(b));gate.close();ok(!gate.finish(b)&&gate.begin("session-c",true)==0);});
        test("subtitle policy upgrades HTTP and preserves raw signed query",()->{ok(SubtitleUrlPolicy.normalize(" http://AISUBTITLE.HDSLB.COM/bfs/subtitle/a%2Fb.json?auth_key=A%2FB%3D ").equals("https://aisubtitle.hdslb.com/bfs/subtitle/a%2Fb.json?auth_key=A%2FB%3D"));ok(SubtitleUrlPolicy.normalize("//i0.hdslb.com/a.json").equals("https://i0.hdslb.com/a.json"));ok(SubtitleUrlPolicy.normalize("https://sub.bilivideo.com/a.json").startsWith("https://"));});
        test("subtitle policy rejects spoofed hosts and never prints signed URL",()->{for(String u:new String[]{"https://hdslb.com.evil.test/a?sig=top-secret","https://evilhdslb.com/a?sig=top-secret","https://user:top-secret@i0.hdslb.com/a","file:///tmp/subtitle","https://i0.hdslb.com:8443/a","https://i0.hdslb.com/"}){boolean rejected=false;try{SubtitleUrlPolicy.normalize(u);}catch(IOException e){rejected=true;ok(!e.getMessage().contains("top-secret"));}ok(rejected);}});
        test("subtitle redirects remain within trusted HTTPS CDN boundaries",()->{ok(SubtitleUrlPolicy.redirect("https://i0.hdslb.com/a/b.json","../c.json?sig=a%2Fb").equals("https://i0.hdslb.com/c.json?sig=a%2Fb"));fails(()->SubtitleUrlPolicy.redirect("https://i0.hdslb.com/a.json","https://outside.test/a.json"));});
        URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL u){return new FakeConnection(u,Proxy.NO_PROXY);}protected URLConnection openConnection(URL u,Proxy p){return new FakeConnection(u,p);}});
        test("network direct-first GET can use configured proxy after failure",()->{ProxySelector original=ProxySelector.getDefault();ProxySelector.setDefault(new ProxySelector(){public List<Proxy> select(URI u){return Collections.singletonList(new Proxy(Proxy.Type.HTTP,new InetSocketAddress("127.0.0.1",9999)));}public void connectFailed(URI u,SocketAddress a,IOException e){}});connections.clear();failDirect=true;try{Net.request("https://test.example/data",null,"","",new Net.Cancel());ok(connections.size()==2);ok(!connections.get(0).usingProxy()&&connections.get(1).usingProxy());}finally{ProxySelector.setDefault(original);failDirect=false;}});
        test("network paid POST never falls back after uncertainty",()->{connections.clear();failDirect=true;try{fails(()->Net.request("https://test.example/data",new JSONObject(),"","fake-test-key",new Net.Cancel()));ok(connections.size()==1);}finally{failDirect=false;}});
        test("network cookies stay on Bilibili API host",()->{connections.clear();Net.request("https://i0.hdslb.com/subtitle",null,"SESSDATA=fake","",new Net.Cancel());ok(connections.get(0).getRequestProperty("Cookie")==null);Net.request("https://api.bilibili.com/test",null,"SESSDATA=fake","",new Net.Cancel());ok(connections.get(1).getRequestProperty("Cookie").equals("SESSDATA=fake"));ok(!connections.get(1).getInstanceFollowRedirects());});
        test("HTTP redirects and risk control are not followed or retried",()->{for(int code:new int[]{302,412,429}){connections.clear();status=code;fails(()->Net.request("https://test.example/data",null,"","",new Net.Cancel()));ok(connections.size()==1);}status=200;});
        test("cancelled task sends no request",()->{connections.clear();Net.Cancel c=new Net.Cancel();c.stop();fails(()->Net.request("https://test.example/data",null,"","",c));ok(connections.isEmpty());});
        test("Bilibili adapter reads login/feed/subtitles through isolated transport",()->{BiliClient c=new BiliClient("SESSDATA=fake");ok(c.username(new Net.Cancel()).equals("测试账号"));JSONArray feed=c.feed(1,new Net.Cancel());ok(feed.length()==1);BiliClient.Transcript t=c.subtitles(feed.getJSONObject(0),12000,new Net.Cancel());ok(t!=null&&t.multiPart&&!t.partial);ok(t.text.contains("先把输入输出写清楚"));});
        test("login verification has short scoped timeouts without changing model defaults",()->{connections.clear();Net.request("https://test.example/data",null,"","",new Net.Cancel().timeouts(8000,10000));ok(connections.get(0).getConnectTimeout()==8000&&connections.get(0).getReadTimeout()==10000);Net.request("https://test.example/data",null,"","",new Net.Cancel());ok(connections.get(1).getReadTimeout()==60000);});
        test("subtitle v2 URL and legacy HTTP work without leaking cookie to CDN",()->{trackOverride=new JSONArray().put(new JSONObject().put("id_str","10000").put("lan","zh-CN").put("subtitle_url","").put("subtitle_url_v2","http://aisubtitle.hdslb.com/subtitle-test.json?sig=A%2FB%3D"));connections.clear();try{BiliClient.Transcript t=new BiliClient("SESSDATA=fake").subtitles(video(),12000,new Net.Cancel());ok(t!=null&&t.sourceHost.equals("aisubtitle.hdslb.com"));FakeConnection last=connections.get(connections.size()-1);ok(last.url().equals("https://aisubtitle.hdslb.com/subtitle-test.json?sig=A%2FB%3D"));ok(last.getRequestProperty("Cookie")==null);ok(last.getRequestProperty("Referer").equals("https://www.bilibili.com/"));}finally{trackOverride=null;}});
        test("empty preferred language does not hide an accessible alternate track",()->{trackOverride=new JSONArray().put(new JSONObject().put("id_str","10000").put("lan","zh-CN").put("subtitle_url","")).put(new JSONObject().put("id_str","10001").put("lan","en").put("lan_doc","英语").put("subtitle_url","//i0.hdslb.com/subtitle-test.json"));try{ok(new BiliClient("").subtitles(video(),12000,new Net.Cancel()).language.equals("英语"));}finally{trackOverride=null;}});
        test("subtitle CDN safe redirect works and external redirect sends no request",()->{String from="https://i0.hdslb.com/subtitle-test.json";redirects.put(from,"https://aisubtitle.hdslb.com/subtitle-test.json");try{ok(new BiliClient("").subtitles(video(),12000,new Net.Cancel()).sourceHost.equals("aisubtitle.hdslb.com"));redirects.put(from,"https://outside.test/private.json?sig=top-secret");connections.clear();fails(()->new BiliClient("").subtitles(video(),12000,new Net.Cancel()));for(FakeConnection c:connections)ok(!c.url().contains("outside.test"));}finally{redirects.clear();}});
        test("inaccessible subtitle is a skippable video failure, not a global login failure",()->{statuses.put("https://i0.hdslb.com/subtitle-test.json",403);try{boolean skipped=false;try{new BiliClient("").subtitles(video(),12000,new Net.Cancel());}catch(BiliClient.SubtitleUnavailable e){skipped=true;}ok(skipped);}finally{statuses.clear();}});
        test("mismatched player metadata is never summarized",()->{playerBvid="BV9999999999";try{fails(()->new BiliClient("").subtitles(video(),12000,new Net.Cancel()));}finally{playerBvid="";}});
        test("selected A never fetches rejected B subtitles or model input",()->{
            crossVideos=true;connections.clear();try{
                BiliClient bili=new BiliClient("");JSONArray feed=bili.feed(1,new Net.Cancel());ok(feed.length()==2&&feed.getJSONObject(0).getString("bvid").equals("BV9999999999"));
                AtomicInteger posts=new AtomicInteger();LlmClient llm=new LlmClient(config(),(u,body,cookie,key,cancel)->{
                    int n=posts.incrementAndGet();if(n==1)return completion(new JSONObject().put("videos",new JSONArray().put(new JSONObject().put("bvid","BV1234567890").put("score",99)))).toString();
                    JSONObject input=new JSONObject(body.getJSONArray("messages").getJSONObject(1).getString("content"));ok(input.getJSONObject("source").getLong("cid")==10);ok(input.getString("subtitles").contains("先把输入输出写清楚"));ok(!input.getString("subtitles").contains("舞蹈"));return completion(summary()).toString();
                });
                List<JSONObject> selected=llm.rank(feed,new Net.Cancel());ok(selected.size()==1);BiliClient.Transcript t=bili.subtitles(selected.get(0),12000,new Net.Cancel());JSONObject result=llm.summarize(t.video(),t,new Net.Cancel());
                SourceBinding.requireVerified(result);ok(result.getString("bvid").equals("BV1234567890")&&result.getLong("cid")==10);ok(result.getString("speech").contains("明确输入输出"));
                ok(posts.get()==2);ok(connections.stream().anyMatch(c->c.url().endsWith("/x/player/v2?bvid=BV1234567890&cid=10")));ok(connections.stream().noneMatch(c->c.url().contains("subtitle-B")||c.url().contains("bvid=BV9999999999")));
                BiliClient.Transcript other=bili.subtitles(new JSONObject().put("bvid","BV9999999999"),12000,new Net.Cancel());ok(other.text.contains("舞蹈")&&!other.text.contains("先把输入输出写清楚"));
            }finally{crossVideos=false;}
        });
        test("detail identity must be present and agree with requested BVID and first-page CID",()->{
            for(String field:new String[]{"bvid","aid","cid","pages"}){detailOverride=detailFixture("BV1234567890");detailOverride.remove(field);try{fails(()->new BiliClient("").subtitles(video(),12000,new Net.Cancel()));}finally{detailOverride=null;}}
            detailOverride=detailFixture("BV9999999999");try{connections.clear();fails(()->new BiliClient("").subtitles(video(),12000,new Net.Cancel()));ok(connections.size()==1);}finally{detailOverride=null;}
            detailOverride=detailFixture("BV1234567890").put("cid",20);try{fails(()->new BiliClient("").subtitles(video(),12000,new Net.Cancel()));}finally{detailOverride=null;}
        });
        test("missing or mismatched player BVID/AID/CID fails closed before CDN",()->{
            for(String field:new String[]{"bvid","aid","cid"})for(boolean missing:new boolean[]{true,false}){
                playerOverride=new JSONObject().put("bvid","BV1234567890").put("aid",100).put("cid",10);if(missing)playerOverride.remove(field);else playerOverride.put(field,field.equals("bvid")?"BV9999999999":999);
                try{connections.clear();fails(()->new BiliClient("").subtitles(video(),12000,new Net.Cancel()));ok(connections.size()==2);}finally{playerOverride=null;}
            }
        });
        test("canonical detail replaces stale candidate title and CID",()->{JSONObject stale=video().put("cid",999).put("title","旧标题");BiliClient.Transcript t=new BiliClient("").subtitles(stale,12000,new Net.Cancel());ok(t.video().getLong("cid")==10&&t.video().getString("title").equals("测试视频"));fails(()->t.requireMatches(stale));});
        test("cross-wired transcript is rejected before any paid model request",()->{AtomicInteger posts=new AtomicInteger();LlmClient llm=new LlmClient(config(),(u,b,c,k,t)->{posts.incrementAndGet();return completion(summary()).toString();});fails(()->llm.summarize(video().put("bvid","BV9999999999"),transcript(),new Net.Cancel()));ok(posts.get()==0);});
        test("transcript snapshots cannot be mutated through returned JSON",()->{BiliClient.Transcript t=transcript();t.video().put("bvid","BV9999999999");t.lines().getJSONObject(0).put("content","被改写");t.source().put("cid",999);t.requireMatches(video());ok(t.lines().getJSONObject(0).getString("content").contains("输入输出"));ok(t.source().getLong("cid")==10);});
        test("correct quotes cannot excuse a wrong model source ID",()->{JSONObject wrong=summary();wrong.getJSONObject("source").put("bvid","BV9999999999");LlmClient llm=new LlmClient(config(),(u,b,c,k,t)->completion(wrong).toString());fails(()->llm.summarize(video(),transcript(),new Net.Cancel()));});
        test("useful summaries require explicit source and topic confirmation",()->{for(String field:new String[]{"sourceMatches","source"}){JSONObject wrong=summary();wrong.remove(field);LlmClient llm=new LlmClient(config(),(u,b,c,k,t)->completion(wrong).toString());fails(()->llm.summarize(video(),transcript(),new Net.Cancel()));}});
        test("legacy summaries remain unverified and hashes contain no signed URL",()->{ok(!SourceBinding.verified(video()));JSONObject v=video().put("source",transcript().source());SourceBinding.requireVerified(v);ok(!v.toString().contains("https://"));v.put("cid",999);fails(()->SourceBinding.requireVerified(v));});
        test("same subtitle document reused across different BVIDs is refused",()->{JSONObject a=video().put("source",transcript().source());JSONObject b=new JSONObject(a.toString()).put("bvid","BV9999999999");b.getJSONObject("source").put("bvid","BV9999999999");fails(()->SourceBinding.rejectReusedSource(b,a));SourceBinding.rejectReusedSource(a,a);});
        test("source viewer links stay on first page and clamp timestamps",()->{ok(SourceNavigation.url("BV1234567890",10).endsWith("?p=1&t=10"));ok(SourceNavigation.url("BV1234567890",-1).endsWith("t=0"));ok(SourceNavigation.url("BV1234567890",999999).endsWith("t=86400"));fails(()->SourceNavigation.url("BV1234567890&redirect=evil",0));});
        test("source viewer blocks app/deeplink/spoofed navigation but accepts official HTTPS",()->{for(String u:new String[]{"bilibili://video/1","intent://video/1","javascript:window.close()","https://bilibili.com.evil.test/","https://user@www.bilibili.com/","http://www.bilibili.com/","https://m.bilibili.com:8443/"})ok(!SourceNavigation.allow(u));ok(SourceNavigation.allow("https://m.bilibili.com/video/BV1234567890"));ok(SourceNavigation.allow("https://passport.bilibili.com/login"));});
        test("pause invalidates in-flight generation while a resume can reserve again",()->{QueuePolicy p=new QueuePolicy(3);long old=p.start();ok(p.reserve(old,1));p.stop();ok(!p.complete(old,1));long resumed=p.start();ok(!p.complete(old,1));ok(p.reserve(resumed,1)&&p.complete(resumed,1));});
        test("enqueue binding rejects a changed transcript fingerprint",()->{BiliClient.Transcript t=transcript();JSONObject v=t.video().put("source",t.source());v.getJSONObject("source").put("documentHash",SourceBinding.hash("other-video"));fails(()->t.requireMatches(v));});
        test("continuous loop survives 30 empty batches then fills all three slots",()->{
            AtomicInteger batches=new AtomicInteger(),pending=new AtomicInteger(),waits=new AtomicInteger();AtomicBoolean active=new AtomicBoolean(true);
            RefillLoop.run(new RefillLoop.Driver(){
                public boolean active(){return active.get();}public boolean awaitRoom(){ok(pending.get()<3);return true;}
                public int produceBatch(){int n=batches.incrementAndGet();if(n<=30)return 0;int p=pending.incrementAndGet();if(p==3)active.set(false);return 1;}
                public void waitForMore(long delay,int empty){ok(delay>0&&delay<=60000&&empty>=5);waits.incrementAndGet();}
            },false);
            ok(batches.get()==33&&pending.get()==3&&waits.get()==26);
        });
        test("empty-batch pacing has no terminal limit and resets on progress",()->{
            RefillPolicy p=new RefillPolicy();for(int i=1;i<=4;i++)ok(p.afterBatch(0)==0);ok(p.afterBatch(0)==5000);ok(p.afterBatch(0)==10000);
            for(int i=0;i<100000;i++)ok(p.afterBatch(0)>0&&p.afterBatch(0)<=60000);ok(p.afterBatch(1)==0&&p.emptyStreak()==0&&p.afterBatch(0)==0);
        });
        test("full buffer blocks the actual loop until consumption then replenishes one",()->{
            Object lock=new Object();AtomicInteger pending=new AtomicInteger(3),calls=new AtomicInteger();AtomicBoolean active=new AtomicBoolean(true);CountDownLatch waiting=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
            Thread thread=new Thread(()->{try{RefillLoop.run(new RefillLoop.Driver(){
                public boolean active(){return active.get();}public boolean awaitRoom()throws Exception{synchronized(lock){while(pending.get()==3&&active.get()){waiting.countDown();lock.wait();}return active.get();}}
                public int produceBatch(){ok(pending.incrementAndGet()==3);calls.incrementAndGet();active.set(false);return 1;}
                public void waitForMore(long ms,int empty){throw new AssertionError();}
            },false);}catch(Throwable t){failure.set(t);}});thread.start();ok(waiting.await(2,TimeUnit.SECONDS));ok(calls.get()==0&&pending.get()==3);
            synchronized(lock){pending.decrementAndGet();lock.notifyAll();}thread.join(2000);ok(!thread.isAlive()&&failure.get()==null&&calls.get()==1&&pending.get()==3);
        });
        test("pause during capacity wait or backoff cannot fetch another batch",()->{
            for(boolean capacity:new boolean[]{true,false}){AtomicBoolean active=new AtomicBoolean(true);AtomicInteger batches=new AtomicInteger();
                RefillLoop.run(new RefillLoop.Driver(){public boolean active(){return active.get();}public boolean awaitRoom(){if(capacity)active.set(false);return true;}
                    public int produceBatch(){batches.incrementAndGet();return 0;}public void waitForMore(long ms,int empty){active.set(false);}},false);
                ok(batches.get()==(capacity?0:5));}
        });
        test("single-video work finishes once without enabling continuous feed",()->{AtomicInteger batches=new AtomicInteger();RefillLoop.run(new RefillLoop.Driver(){public boolean active(){return true;}public boolean awaitRoom(){return true;}public int produceBatch(){batches.incrementAndGet();return 0;}public void waitForMore(long ms,int empty){throw new AssertionError();}},true);ok(batches.get()==1);});
        test("transient subtitle GET retries in place without repeating paid ranking",()->{
            AtomicInteger ranking=new AtomicInteger(1),reads=new AtomicInteger(),waits=new AtomicInteger();String value=RefillLoop.read(()->{if(reads.incrementAndGet()<4)throw new java.net.SocketTimeoutException("fixture");return "subtitle-A";},new RefillLoop.ReadWaiter(){public void check(){}public void waitBeforeRetry(long ms,int attempt,Exception e){waits.incrementAndGet();ok(ms==RefillPolicy.readRetryDelay(attempt));}},true);
            ok(value.equals("subtitle-A")&&reads.get()==4&&waits.get()==3&&ranking.get()==1);
        });
        test("read retries stop immediately on pause and never impose a session quota",()->{
            AtomicInteger reads=new AtomicInteger(),waits=new AtomicInteger();AtomicBoolean stopped=new AtomicBoolean();
            fails(()->RefillLoop.read(()->{reads.incrementAndGet();throw new java.net.UnknownHostException();},new RefillLoop.ReadWaiter(){public void check()throws Exception{if(stopped.get())throw new IOException("cancelled");}public void waitBeforeRetry(long ms,int attempt,Exception e){if(waits.incrementAndGet()==20)stopped.set(true);ok(ms<=120000);}},true));ok(reads.get()==20&&waits.get()==20);
        });
        test("auth, risk, certificate and configuration errors are not silently auto-retried",()->{
            for(Exception error:new Exception[]{new Net.HttpFailure(401),new Net.HttpFailure(403),new Net.HttpFailure(412),new Net.HttpFailure(429),new javax.net.ssl.SSLHandshakeException("fixture"),new IOException("malformed response")}){
                AtomicInteger reads=new AtomicInteger();fails(()->RefillLoop.read(()->{reads.incrementAndGet();throw error;},new RefillLoop.ReadWaiter(){public void check(){}public void waitBeforeRetry(long ms,int attempt,Exception e){throw new AssertionError();}},true));ok(reads.get()==1);
            }ok(RefillPolicy.retryableRead(new Net.HttpFailure(503)));ok(RefillPolicy.retryableRead(new Net.HttpFailure(408)));
        });
        test("single-video diagnostics never enter the continuous read-retry loop",()->{AtomicInteger calls=new AtomicInteger();fails(()->RefillLoop.read(()->{calls.incrementAndGet();throw new SocketTimeoutException();},new RefillLoop.ReadWaiter(){public void check(){}public void waitBeforeRetry(long ms,int attempt,Exception e){throw new AssertionError();}},false));ok(calls.get()==1);});
        test("only selection-affecting settings flag unapplied preferences",()->{
            JSONObject a=config().put("blocked","娱乐").put("minScore",65).put("inputChars",12000),b=new JSONObject(a.toString());String signature=RefillPolicy.preferences(a);
            ok(!RefillPolicy.preferences(new JSONObject().put("interests","A\nB").put("blocked","C")).equals(RefillPolicy.preferences(new JSONObject().put("interests","A").put("blocked","B\nC"))));
            b.put("pendingLimit",8).put("speechRate",1.5).put("autoPlay",false);ok(signature.equals(RefillPolicy.preferences(b)));
            for(String key:new String[]{"interests","blocked","minScore","inputChars"}){JSONObject changed=new JSONObject(a.toString());changed.put(key,key.equals("minScore")?80:key.equals("inputChars")?24000:"另一组偏好");ok(!signature.equals(RefillPolicy.preferences(changed)));}
        });
        test("continuous listening refills 1000 times without a batch or request quota",()->{
            AtomicInteger pending=new AtomicInteger(),consumed=new AtomicInteger(),produced=new AtomicInteger(),batches=new AtomicInteger();AtomicBoolean active=new AtomicBoolean(true);
            RefillLoop.run(new RefillLoop.Driver(){public boolean active(){return active.get();}public boolean awaitRoom(){if(pending.get()==3){pending.decrementAndGet();consumed.incrementAndGet();}return true;}
                public int produceBatch(){if(batches.incrementAndGet()%4!=0)return 0;ok(pending.incrementAndGet()<=3);if(produced.incrementAndGet()==1000)active.set(false);return 1;}
                public void waitForMore(long ms,int empty){throw new AssertionError();}},false);
            ok(produced.get()==1000&&pending.get()==3&&consumed.get()==997&&batches.get()==4000);
        });
        test("paid work failure leaves the refill loop instead of blindly reposting",()->{
            AtomicInteger paid=new AtomicInteger();fails(()->RefillLoop.run(new RefillLoop.Driver(){public boolean active(){return true;}public boolean awaitRoom(){return true;}public int produceBatch()throws Exception{paid.incrementAndGet();throw new SocketTimeoutException("uncertain paid POST");}public void waitForMore(long ms,int empty){throw new AssertionError();}},false));ok(paid.get()==1);
        });
        System.out.println("TOTAL "+passed+" passed; no real API calls were made.");
    }
}