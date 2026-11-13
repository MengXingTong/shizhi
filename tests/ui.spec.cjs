const fs=require('node:fs');
const path=require('node:path');
const http=require('node:http');
const assert=require('node:assert/strict');
let pw;try{pw=require('playwright');}catch{pw=require(path.join(process.env.SHIZHI_NODE_MODULES||path.join(process.env.USERPROFILE,'.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules'),'playwright'));}
const root=path.resolve(__dirname,'..');const assets=path.join(root,'app/src/main/assets');
const defaults={baseUrl:'https://api.openai.com/v1',model:'',tokenParam:'max_tokens',hasKey:false,loggedIn:false,username:'',pendingLimit:3,interests:'',blocked:'',speechRate:1,autoPlay:true,inputChars:12000,minScore:65};
const state={settings:{...defaults},native:true,scanPaused:false,reviewCount:0,scanning:false,retryKind:"",retryRemainingMs:0,preferencesPending:false,waitingForNext:false,errorAction:"",workerActive:false,speaking:false,pending:0,pendingLimit:3,queue:[],history:[],favorites:[],current:null,phase:'idle',message:'按你的兴趣，留住有用的内容',lastSkip:'',ttsReady:true,ttsStatus:'测试替身：中文语音可用',speechProgress:0,scanned:0,skipped:0,produced:0,requests:0,tokens:null};
const item={sourceVerified:true,cid:10,source:{cid:10},id:1,bvid:'BV1234567890',title:'测试摘要，不是真实提炼结果',author:'测试作者',summary:'这是一条仅用于界面自动化的摘要。',speech:'测试语音',duration:600,estimatedSeconds:60,created:Date.now(),favorite:false,status:'queued',scope:'完整可用字幕',subtitleLanguage:'中文',points:[{text:'界面必须显示可核对的来源。',at:10,quote:'测试字幕原文'}]};
let moreResults=[],confirmRestart=true,holdProbe=false,releaseProbe=null;const calls=[];let page,browser,server;let passed=0;
async function test(name,run){await run();passed++;console.log('PASS '+name);}
(async()=>{try{
  server=http.createServer((req,res)=>{const name=req.url.split('?')[0].slice(1)||'index.html';if(!['index.html','styles.css','app.js','lucide.min.js'].includes(name)){res.writeHead(404);res.end();return;}res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(assets,name)));});await new Promise(r=>server.listen(0,'127.0.0.1',r));
  const executable=process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe';browser=await pw.chromium.launch({executablePath:executable,headless:true,args:['--no-proxy-server']});
  page=await browser.newPage({viewport:{width:390,height:844}});page.setDefaultTimeout(5000);const errors=[];page.on('pageerror',e=>errors.push(e.message));const requests=[];page.on('request',r=>requests.push(r.url()));
  await page.exposeFunction('__nativePost',async raw=>{const req=JSON.parse(raw);calls.push(req);let result=true;
    switch(req.action){
      case'state':result=state;break;
      case'saveApi':Object.assign(state.settings,{baseUrl:req.payload.baseUrl,model:req.payload.model,tokenParam:req.payload.tokenParam,hasKey:!!req.payload.apiKey||state.settings.hasKey});if(req.payload.clearKey)state.settings.hasKey=false;break;
      case'saveFeature':if(state.scanning&&['interests','blocked','minScore','inputChars'].some(k=>req.payload[k]!==state.settings[k]))state.preferencesPending=true;for(const k of ['pendingLimit','interests','blocked','speechRate','autoPlay','inputChars','minScore'])state.settings[k]=req.payload[k];state.pendingLimit=state.settings.pendingLimit;break;
      case'login':state.settings.loggedIn=true;state.settings.username='测试账号（模拟）';break;
      case'logout':state.settings.loggedIn=false;state.settings.username='';break;
      case'start':if(!confirmRestart){result=false;break;}state.preferencesPending=false;for(const x of state.history)if(x.status==='queued')x.status='archived';state.queue=[];state.pending=0;state.scanning=true;state.scanPaused=false;state.message='测试替身：新一轮刷取';break;
      case'pauseScan':state.scanning=false;state.workerActive=false;state.scanPaused=true;state.phase='paused';break;
      case'resumeScan':state.scanning=true;state.scanPaused=false;break;
      case'list':result=moreResults;break;
      case'reextract':break;
      case'stop':state.scanning=false;state.message='已停止';break;
      case'toggle':if(!state.current&&!state.queue.length&&state.scanning)state.waitingForNext=true;else state.speaking=!state.speaking;break;
      case'pausePlayback':state.waitingForNext=false;state.speaking=false;break;
      case'next':state.playbackNotice=state.scanning?'暂无下一条，正在查找与提炼；进度见上方运行状态。':'刷取当前未运行。请查看上方停止原因，再点「刷视频」开始。';break;
      case'favorite':{const x=state.history.find(x=>x.id===req.payload.id);if(x){x.favorite=!x.favorite;state.favorites=state.history.filter(x=>x.favorite);}break;}
      case'play':state.current=state.history.find(x=>x.id===req.payload.id);state.speaking=true;state.queue=state.queue.filter(x=>x.id!==req.payload.id);state.pending=state.queue.length;break;
      case'testApi':result={ok:true};break;
      case'probe':if(holdProbe)await new Promise(resolve=>{releaseProbe=resolve;});result={username:'模拟账号',title:'测试视频',subtitleAvailable:true,characters:120,scope:'模拟字幕'};break;
    }
    await page.evaluate(m=>window.ShizhiNative.receive(m),{id:req.id,ok:true,data:result});
  });
  await page.addInitScript(()=>{window.AndroidBridge={postMessage(raw){window.__nativePost(raw);}};});
  await page.goto('http://127.0.0.1:'+server.address().port);await page.getByRole('heading',{name:'先准备好，再开始',exact:true}).waitFor();
  const emit=async()=>page.evaluate(s=>window.ShizhiNative.receive({event:'state',data:s}),state);
  await test('no fake summaries in first-use screen',async()=>{assert.equal(await page.locator('.sz-list-row').count(),0);assert.equal(await page.locator('.sz-player-title').count(),0);assert.equal(await page.locator('.sz-bottom button').count(),2);assert.equal(await page.getByRole('tab').count(),4);});
  await test('global API settings do not leak saved key back to page',async()=>{await page.locator('.sz-bottom [data-value="global"]').click();await page.locator('[data-api="baseUrl"]').fill('https://model.example/v1');await page.locator('[data-api="apiKey"]').fill('fake-ui-test-key');await page.locator('[data-api="model"]').fill('test-model');await page.locator('[data-action="saveApi"]').click();await page.waitForFunction(()=>document.querySelector('#sz-api-key').value==='');assert.equal(state.settings.hasKey,true);assert.equal(state.settings.apiKey,undefined);});
  await test('feature settings contain capacity but no request quota',async()=>{await page.locator('[data-action="feature"]').click();assert.equal(await page.locator('#sz-cap').innerText(),'3 条');assert.doesNotMatch(await page.locator('.sz-content').innerText(),/每轮|请求上限/);await page.locator('[data-action="capUp"]').click();await page.locator('[data-feature="interests"]').fill('测试：学习技术');await page.locator('[data-action="saveFeature"]').click();await page.waitForFunction(()=>document.querySelector('#sz-feedback-text').textContent.includes('已保存'));assert.equal(state.settings.pendingLimit,4);assert.equal(state.settings.interests,'测试：学习技术');});
  await test('background state events do not destroy unsaved input',async()=>{await page.locator('[data-feature="interests"]').fill('未保存的草稿');await emit();assert.equal(await page.locator('[data-feature="interests"]').inputValue(),'未保存的草稿');});
  await test('login username refreshes before Save without overwriting drafts or focus',async()=>{
    await page.locator('[data-action="capDown"]').click();
    await page.locator('[data-feature="interests"]').fill('尚未保存的兴趣草稿');
    await page.locator('[data-feature="interests"]').focus();
    await page.locator('[data-feature="interests"]').evaluate(e=>e.setSelectionRange(2,4));
    const saves=calls.filter(x=>x.action==='saveFeature').length;
    state.settings.loggedIn=true;state.settings.username='验证码登录的新用户名';await emit();
    assert.match(await page.locator('.sz-content').innerText(),/验证码登录的新用户名/);
    assert.equal(await page.locator('[data-action="logout"]').count(),1);
    assert.equal(await page.locator('[data-feature="interests"]').inputValue(),'尚未保存的兴趣草稿');
    assert.equal(await page.locator('#sz-cap').innerText(),'3 条');
    assert.deepEqual(await page.locator('[data-feature="interests"]').evaluate(e=>({focused:document.activeElement===e,start:e.selectionStart,end:e.selectionEnd})),{focused:true,start:2,end:4});
    assert.equal(calls.filter(x=>x.action==='saveFeature').length,saves);
  });
  await test('logout refreshes account independently and username cannot inject markup',async()=>{
    state.settings.username='<img src=x onerror="window.PWNED=true">';await emit();
    assert.match(await page.locator('#sz-account-state').innerText(),/<img src=x/);
    assert.equal(await page.locator('#sz-account-state img').count(),0);
    state.settings.loggedIn=false;state.settings.username='';await emit();
    assert.equal(await page.locator('#sz-account-state [data-action="login"]').count(),1);
    assert.doesNotMatch(await page.locator('#sz-account-state').innerText(),/验证码登录的新用户名/);
    assert.equal(await page.locator('[data-feature="interests"]').inputValue(),'尚未保存的兴趣草稿');
  });
  await test('subtitle diagnosis uses native probe without a model action',async()=>{await page.locator('#sz-bvid').fill('https://www.bilibili.com/video/BV1234567890');const n=calls.length;await page.locator('[data-action="probe"]').click();await page.waitForFunction(()=>document.querySelector('#sz-feedback-text').textContent.includes('未调用模型'));assert.deepEqual(calls.slice(n).map(c=>c.action),['probe']);});
  await test('history/favorites and cached playback remain inside video feature',async()=>{state.history=[{...item}];state.queue=state.history;state.pending=1;await page.getByRole('tab',{name:'历史',exact:true}).click();await emit();await page.locator('[data-action="favorite"]').click();await page.getByRole('tab',{name:'收藏',exact:true}).click();assert.equal(await page.locator('.sz-list-row').count(),1);const n=calls.length;await page.locator('[data-action="play"]').click();await page.waitForFunction(()=>document.querySelector('#sz-mini').textContent.includes('正在播报'));assert.ok(!calls.slice(n).some(x=>['start','addVideo','testApi'].includes(x.action)));});
  await test('untrusted titles are rendered as text, not active HTML',async()=>{state.history[0].title='<img src=x onerror="window.PWNED=true">';await page.getByRole('tab',{name:'历史',exact:true}).click();await emit();assert.equal(await page.locator('.sz-list-title').innerText(),state.history[0].title);assert.equal(await page.locator('.sz-content img').count(),0);assert.equal(await page.evaluate(()=>window.PWNED),undefined);});
  await test('live runtime panel reports each real production phase and elapsed step time',async()=>{
    await page.getByRole('tab',{name:'待听',exact:true}).click();
    state.scanning=true;state.workerActive=true;state.scanned=12;state.skipped=8;state.produced=1;state.pending=0;state.stageStartedAt=Date.now()-2000;state.activeVideo='测试中的候选视频';await emit();await page.locator('[data-runtime-details] > summary').click();
    for(const [phase,label] of [['checking','确认登录'],['discovering','获取推荐'],['ranking','兴趣筛选'],['subtitles','读取字幕'],['summarizing','提炼内容'],['validating','校验引用'],['queued','加入待播']]){
      state.phase=phase;state.message='真实阶段测试：'+label;await emit();assert.match(await page.locator('.sz-runtime').innerText(),new RegExp(label));assert.match(await page.locator('.sz-runtime-counts').innerText(),/已检查 12/);
    }
    state.phase='subtitles';state.message='读取字幕中';state.stageStartedAt=Date.now()-2000;await emit();const n=calls.length;
    await page.waitForFunction(()=>{const t=document.querySelector('#sz-stage-age').textContent;return /本步骤 [3-9][0-9]* 秒/.test(t);});
    assert.equal(calls.length,n,'elapsed display must not poll native or model APIs');
    state.phase='buffered';state.pending=state.pendingLimit;state.message='待播已满，已暂停筛选与提炼';await emit();assert.match(await page.locator('.sz-runtime').innerText(),/待播已满 · 自动补货待命/);
  });
  await test('playback next cannot hide a stopped production error behind waiting text',async()=>{
    state.phase='error';state.failedPhase='subtitles';state.scanning=false;state.workerActive=false;state.pending=0;state.queue=[];state.message='测试错误：字幕 CDN 未识别：unknown.example';state.playbackNotice='';await emit();
    await page.getByRole('button',{name:'下一个',exact:true}).click();
    await page.waitForFunction(()=>document.querySelector('.sz-playback-notice')?.textContent.includes('刷取当前未运行'));
    assert.match(await page.locator('.sz-runtime').innerText(),/需要处理/);assert.match(await page.locator('.sz-runtime-message').innerText(),/unknown.example/);assert.match(await page.locator('.sz-runtime-failed').innerText(),/读取字幕/);
    assert.doesNotMatch(await page.locator('.sz-runtime-message').innerText(),/等待补货/);
  });
  await test('single-video completion shows last skip reason without fake waiting',async()=>{
    state.current=null;state.phase='completed';state.message='指定视频没有可用结果';state.lastSkip='此候选没有可用字幕，未调用摘要模型';state.playbackNotice='';await emit();
    assert.match(await page.locator('.sz-runtime').innerText(),/单条任务已处理/);assert.match(await page.locator('.sz-runtime-skip').innerText(),/没有可用字幕/);
  });
  await test('runtime error panel remains readable on narrow screens',async()=>{
    state.phase='error';state.failedPhase='subtitles';state.message='测试错误：字幕 CDN 未识别：a-long-subtitle-source.example';state.activeVideo='自动化测试候选，不是真实视频';state.scanning=false;state.stageStartedAt=Date.now();await emit();
    for(const width of [320,390,736]){await page.setViewportSize({width,height:900});const overflow=await page.locator('.sz-runtime').evaluate(r=>{const a=r.getBoundingClientRect();return [...r.querySelectorAll('*')].filter(e=>{const b=e.getBoundingClientRect();return b.width&&(b.left<a.left-1||b.right>a.right+1);}).map(e=>e.className);});assert.deepEqual(overflow,[]);}
    await page.setViewportSize({width:390,height:900});
    await page.locator('.sz-runtime').evaluate(e=>{const p=document.createElement('p');p.id='sz-test-only-label';p.textContent='自动化测试示例 · 非真实 B 站结果';e.prepend(p);});
    await page.locator('.sz-runtime').screenshot({path:path.join(root,'dist/runtime-panel-test-fixture.png')});
    await page.locator('#sz-test-only-label').evaluate(e=>e.remove());
  });
  await test('responsive screen fits at 320/390/736 pixels',async()=>{for(const width of [320,390,736]){await page.setViewportSize({width,height:900});await page.getByRole('tab',{name:'功能设置',exact:true}).click();const overflow=await page.locator('.sz-product').evaluate(r=>[...r.querySelectorAll('*')].filter(e=>{const a=r.getBoundingClientRect(),b=e.getBoundingClientRect();return b.width&&(b.left<a.left-1||b.right>a.right+1);}).map(e=>e.className));assert.deepEqual(overflow,[]);}});
  await test('saving preferences alone preserves the pending queue',async()=>{
    state.scanning=false;state.workerActive=false;state.scanPaused=false;state.queue=[{...item,id:21,status:'queued',favorite:true}];state.history=[...state.queue];state.favorites=[...state.queue];state.pending=1;
    await page.getByRole('tab',{name:'功能设置',exact:true}).click();await emit();const before=calls.length;await page.locator('[data-feature="interests"]').fill('新的学习偏好');await page.locator('[data-action="saveFeature"]').click();await page.waitForFunction(()=>document.querySelector('#sz-feedback-text').textContent.includes('功能设置已保存'));
    assert.equal(state.queue.length,1);assert.equal(state.favorites.length,1);assert.ok(!calls.slice(before).some(c=>['start','resumeScan','pauseScan'].includes(c.action)));
  });
  await test('new scan and pause/resume are separate commands with explicit queue semantics',async()=>{
    await page.getByRole('tab',{name:'待听',exact:true}).click();state.current={...item,id:22,status:'active'};state.speaking=true;await emit();const n=calls.length;
    assert.equal(await page.getByRole('button',{name:'重新刷视频',exact:true}).count(),1);await page.getByRole('button',{name:'重新刷视频',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[data-action="pauseScan"]'));
    assert.equal(state.pending,0);assert.equal(state.current.id,22);assert.equal(state.speaking,true);assert.equal(state.history.length,1);assert.equal(state.history[0].status,'archived');assert.equal(state.favorites.length,1);assert.equal(calls.slice(n).filter(c=>c.action==='start').length,1);
    state.queue=[{...item,id:23}];state.history.push(...state.queue);state.pending=1;await emit();await page.getByRole('button',{name:'暂停刷取',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[data-action="resumeScan"]'));
    assert.equal(state.queue.length,1);assert.equal(await page.getByRole('button',{name:'重新刷视频',exact:true}).count(),1);assert.match(await page.locator('#sz-panel').innerText(),/继续不会清空队列/);
    const pauseN=calls.length;await page.getByRole('button',{name:'继续刷取',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[data-action="pauseScan"]'));assert.equal(state.queue.length,1);assert.equal(state.pending,1);assert.ok(calls.slice(pauseN).some(c=>c.action==='resumeScan'));assert.ok(!calls.slice(pauseN).some(c=>c.action==='start'));
    state.scanning=false;state.scanPaused=false;state.workerActive=false;
  });
  await test('legacy source warning blocks play but keeps source, favorite, and explicit repair',async()=>{
    state.current=null;state.speaking=false;state.queue=[];state.pending=0;state.history=[{...item,id:31,sourceVerified:false,status:'review',favorite:true}];state.favorites=[...state.history];state.reviewCount=1;await page.getByRole('tab',{name:'历史',exact:true}).click();await emit();await page.locator('[data-action="inspect"]').click();assert.match(await page.locator('.sz-detail').innerText(),/旧版本内容未校验字幕来源/);assert.equal(await page.locator('.sz-detail [data-action="play"]').count(),0);assert.equal(await page.getByRole('button',{name:'打开原视频',exact:true}).count(),1);assert.equal(await page.locator('.sz-detail [data-action="favorite"]').count(),1);
    const before=calls.length;await page.getByRole('button',{name:'重新提炼这条视频',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.sz-detail'));assert.ok(calls.slice(before).some(c=>c.action==='reextract'&&c.payload.id===31));assert.ok(!calls.slice(before).some(c=>c.action==='start'));await page.locator('[data-action="back"]').click();
  });
  await test('source timestamp action sends the displayed BVID, not another list item',async()=>{
    state.history=[{...item,id:41,title:'视频A'},{...item,id:42,bvid:'BV9999999999',title:'视频B',points:[{text:'B知识点',at:73,quote:'B字幕原文'}]}];state.favorites=[];await emit();await page.locator('[data-action="inspect"][data-id="42"]').click();const before=calls.length;await page.getByRole('button',{name:'打开原视频时间点'}).click();await page.waitForFunction(()=>document.querySelector('.sz-detail'));await page.waitForTimeout(50);assert.deepEqual(calls.slice(before).filter(c=>c.action==='openSource').map(c=>c.payload),[{bvid:'BV9999999999',at:73}]);await page.locator('[data-action="back"]').click();
  });
  await test('current native summary wins over stale paginated history with the same ID',async()=>{
    state.history=Array.from({length:50},(_,i)=>({...item,id:100+i}));state.current=null;moreResults=[{...item,id:52,title:'旧缓存标题',summary:'旧缓存字幕'}];await emit();await page.locator('[data-action="more"][data-kind="history"]').click();await page.waitForFunction(()=>document.querySelector('[data-action="inspect"][data-id="52"]'));state.history=[{...item,id:52,title:'新来源标题',summary:'新来源字幕'}];moreResults=[];await emit();await page.locator('[data-action="inspect"][data-id="52"]').click();assert.match(await page.locator('.sz-detail').innerText(),/新来源字幕/);assert.doesNotMatch(await page.locator('.sz-detail').innerText(),/旧缓存字幕/);await page.locator('[data-action="back"]').click();
  });
  await test('many empty batches remain active with a local countdown and no manual restart',async()=>{
    await page.getByRole('tab',{name:'待听',exact:true}).click();state.current=null;state.queue=[];state.pending=0;state.scanPaused=false;state.scanning=true;state.workerActive=true;state.phase='waiting';state.retryKind='empty';state.retryRemainingMs=5000;state.page=30;state.message='暂未找到合适内容，稍后自动继续翻页；无需重新开始';await emit();
    assert.match(await page.locator('.sz-runtime').innerText(),/继续为你找/);assert.match(await page.locator('.sz-buffer-target').innerText(),new RegExp('还在为你补 '+state.pendingLimit+' 条'));assert.equal(await page.getByRole('button',{name:'暂停刷取',exact:true}).count(),1);assert.equal(await page.locator('[data-action="resumeScan"]').count(),0);assert.doesNotMatch(await page.locator('.sz-runtime').innerText(),/查找已结束/);
    const before=calls.length;await page.waitForFunction(()=>{const n=parseInt(document.querySelector('#sz-retry-countdown').textContent);return n>0&&n<=3;});assert.equal(calls.length,before,'countdown must not poll the bridge or model');
    for(const width of [320,390,736]){await page.setViewportSize({width,height:900});const overflow=await page.locator('.sz-product').evaluate(r=>[...r.querySelectorAll('*')].filter(e=>{const a=r.getBoundingClientRect(),b=e.getBoundingClientRect();return b.width&&(b.left<a.left-1||b.right>a.right+1);}).map(e=>e.className));assert.deepEqual(overflow,[]);}
    await page.setViewportSize({width:390,height:900});await page.locator('.sz-runtime').evaluate(e=>{const p=document.createElement('p');p.textContent='浏览器测试示例 · 非真机/真实筛选结果';e.prepend(p);});await page.locator('.sz-runtime').screenshot({path:path.join(root,'dist/continuous-refill-test-fixture.png')});
  });
  await test('network wait stays active and never asks for a new paid search',async()=>{
    state.phase='retrying';state.retryKind='network';state.retryRemainingMs=30000;state.message='网络读取暂时失败，稍后自动重试；无需重新开始';await emit();assert.match(await page.locator('.sz-runtime').innerText(),/将自动重试/);assert.match(await page.locator('#sz-retry-countdown').innerText(),/自动重试读取/);assert.equal(await page.getByRole('button',{name:'暂停刷取',exact:true}).count(),1);
  });
  await test('actionable API error offers settings and retry without clearing queue',async()=>{
    state.scanning=false;state.workerActive=false;state.scanPaused=true;state.phase='error';state.errorAction='api';state.message='模型接口需要检查，已有待播保留';state.queue=[{...item,id:201}];state.history=[...state.queue];state.pending=1;await emit();assert.equal(await page.getByRole('button',{name:'检查模型接口设置'}).count(),1);const before=calls.length;await page.getByRole('button',{name:'保留队列重试补货'}).click();await page.waitForFunction(()=>document.querySelector('[data-action="pauseScan"]'));assert.equal(state.pending,1);assert.ok(calls.slice(before).some(c=>c.action==='resumeScan'));assert.ok(!calls.slice(before).some(c=>c.action==='start'));
  });
  await test('restarting can be cancelled without touching pending or current playback',async()=>{
    state.current={...item,id:202};state.speaking=true;confirmRestart=false;await emit();await page.getByRole('button',{name:'重新刷视频',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[data-action="pauseScan"]'));assert.equal(state.pending,1);assert.equal(state.current.id,202);assert.equal(state.speaking,true);confirmRestart=true;
  });
  await test('saved new preferences are marked unapplied without changing unsaved drafts',async()=>{
    state.preferencesPending=false;await page.getByRole('tab',{name:'功能设置',exact:true}).click();await emit();await page.locator('[data-feature="interests"]').fill('新目标：系统设计');await page.locator('[data-action="saveFeature"]').click();await page.waitForFunction(()=>document.querySelector('#sz-feature-apply').textContent.includes('尚未应用'));const before=calls.length;await page.locator('[data-feature="interests"]').fill('尚未保存的另一个草稿');await emit();assert.equal(await page.locator('[data-feature="interests"]').inputValue(),'尚未保存的另一个草稿');assert.ok(!calls.slice(before).some(c=>c.action==='start'));await page.getByRole('tab',{name:'待听',exact:true}).click();assert.match(await page.locator('.sz-preference-notice').innerText(),/尚未应用/);
  });
  await test('empty-queue playback waits for fresh content and can cancel without replaying history',async()=>{
    state.current=null;state.queue=[];state.pending=0;state.history=[{...item,id:203,status:'played'}];state.waitingForNext=false;state.preferencesPending=false;state.scanning=true;state.phase='discovering';state.errorAction='';state.message='查找下一批';await emit();const before=calls.length;await page.getByRole('button',{name:'准备好后播放',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[data-action="pausePlayback"]'));assert.equal(state.current,null);assert.match(await page.locator('.sz-player').innerText(),/不重播旧内容/);assert.ok(!calls.slice(before).some(c=>c.action==='play'));await page.getByRole('button',{name:'取消等待播放',exact:true}).click();await page.waitForFunction(()=>!document.querySelector('[data-action="pausePlayback"]'));assert.equal(state.waitingForNext,false);
  });
  await test('current listening comes before diagnostics and single extraction is not advertised as endless feed',async()=>{
    state.current={...item,id:301};state.speaking=true;state.preferencesPending=false;state.runMode='continuous';state.phase='ranking';state.message='按兴趣筛选下一批';await emit();assert.equal(await page.evaluate(()=>!!(document.querySelector('.sz-player').compareDocumentPosition(document.querySelector('.sz-runtime'))&Node.DOCUMENT_POSITION_FOLLOWING)),true);
    state.runMode='single';await emit();assert.match(await page.locator('.sz-runtime h3').innerText(),/正在处理指定视频/);assert.doesNotMatch(await page.locator('.sz-buffer-target').innerText(),/还在为你补/);state.scanning=false;state.workerActive=false;state.scanPaused=true;state.phase='error';await emit();assert.equal(await page.getByRole('button',{name:'保留队列重试这条'}).count(),1);state.runMode='continuous';
  });
  await test('long subtitle diagnosis never blocks playback pause or other media controls',async()=>{
    state.phase='paused';state.scanPaused=true;state.scanning=false;state.workerActive=false;state.current={...item,id:401};state.speaking=true;await page.getByRole('tab',{name:'功能设置',exact:true}).click();await emit();holdProbe=true;releaseProbe=null;await page.locator('[data-action="probe"]').click();await page.waitForFunction(()=>document.querySelector('[data-action="probe"]').disabled);
    await page.locator('#sz-mini [data-action="toggle"]').click();await page.waitForFunction(()=>document.querySelector('#sz-mini').textContent.includes('已暂停'));assert.equal(state.speaking,false);assert.ok(releaseProbe);releaseProbe();holdProbe=false;await page.waitForFunction(()=>!document.querySelector('[data-action="probe"]').disabled);
  });
  await test('assets do not perform browser API calls or load remote resources',async()=>{assert.ok(requests.every(url=>url.startsWith('http://127.0.0.1:')));assert.deepEqual(errors,[]);});
  // Real assets without a mock bridge: capture honest first-use UI, not a fake end-to-end success.
  const preview=await browser.newPage({viewport:{width:390,height:844}});await preview.goto('http://127.0.0.1:'+server.address().port);await preview.locator('.sz-player').waitFor();await preview.screenshot({path:path.join(root,'dist/android-first-use.png'),fullPage:true});await preview.getByRole('tab',{name:'功能设置',exact:true}).click();await preview.screenshot({path:path.join(root,'dist/android-feature-settings.png'),fullPage:true});
  console.log('TOTAL '+passed+' UI checks passed. Native commands used test doubles, not Android or live Bilibili.');
}catch(e){console.error(e);process.exitCode=1;}finally{if(browser)await browser.close();if(server)server.close();}})();