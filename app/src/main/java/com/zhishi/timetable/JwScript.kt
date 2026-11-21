package com.zhishi.timetable

import org.json.JSONObject

/**
 * 注入教务网页的取数脚本：按教务类型带着登录态直接请求课表接口（用户不用先点到课表页），
 * 请求不到再退回读当前页面（含 iframe / frame 里嵌的页面）。结果通过 ZhishiJw.post 回传 JSON，
 * 每走一步用 ZhishiJw.step 报进度。
 *
 * 回传：{ok, token, kind, json?, html:[...], diag:{step, http, path, login, loginPage, found, tried:[路径]}}
 *  - diag 只放步骤、HTTP 状态码、接口路径（不带查询参数），不放 Cookie、账号、学号、课表内容。
 *  - 每个请求 20 秒超时（window.__jwTimeout 可改，单测用）。
 *
 * 接口来源：
 *  - 金智（东大）：原有逻辑，请求和参数不变（见 Jw.kt 开头）。
 *  - 正方：xskbcx_cxXsKb / xskbcx_cxXsgrkb（baoozak/timetable，MIT）；带 kzlx=ck、xsdm 的 cxXsgrkb?gnmkdm=N2151 是新正方 V9 的写法
 *    （中国矿业大学等；只参考了 kqakqakqa/xiaoai-schedule-parser 里的接口名和参数名，该仓库没有许可证，没有复制代码）。
 *  - 强智 xskb_list.do、新 URP thisSemesterCurriculum、旧 URP xkAction、超星 sdpkkbList：shiguang_warehouse（MIT）及原有实现。
 *  - 树维 eams：courseTableForStd!courseTable.action 参数与电子科大学期 id 规律（shiguang_warehouse resources/UESTC，MIT）。
 *  - 青果新版 wsxk/xkjg.ckdgxsxdkchj_data10319.jsp?params=base64(xn=..&xq=..)（shiguang_warehouse resources/HIST，MIT）；没有真实账号验证过。
 * 详见 THIRD_PARTY_NOTICES.md。
 */
fun jwScript(kind: String, arg: String?, token: Int = 0): String {
    val k = JSONObject.quote(kind); val a = if (arg == null) "null" else JSONObject.quote(arg)
    return """
(async function(){
const K=$k, ARG=$a, TOKEN=$token, TMO=(window.__jwTimeout||20000);
const D={step:'start',http:null,path:null,login:false,loginPage:false,found:false,tried:[]};
const P=o=>{o.token=TOKEN;o.diag=D;try{ZhishiJw.post(JSON.stringify(o))}catch(e){}};
const S=s=>{D.step=s;try{ZhishiJw.step(TOKEN,s)}catch(e){}};
function docs(){const out=[];const seen=new Set();function walk(w,d){if(d>4)return;try{const doc=w.document;if(!doc||seen.has(doc))return;seen.add(doc);out.push(doc);for(const f of doc.querySelectorAll('iframe,frame')){try{if(f.contentWindow)walk(f.contentWindow,d+1)}catch(e){}}}catch(e){}}walk(window,0);try{if(window.top!==window)walk(window.top,0)}catch(e){}return out;}
function htmls(){return docs().map(d=>{try{return d.documentElement.outerHTML}catch(e){return ''}});}
function hrefs(){const out=[];for(const d of docs()){try{out.push(d.location.href)}catch(e){}try{for(const e of d.querySelectorAll('a[href],iframe[src],frame[src]'))out.push(e.href||e.src)}catch(e){}}return out;}
function base(marker){for(const h of hrefs()){const i=h.indexOf(marker);if(i>=0)return h.substring(0,i);}return null;}
function pathOf(u){try{return new URL(u,location.href).pathname}catch(e){return ''}}
function looksLogin(t){return /type\s*=\s*["']?password/i.test(t||'')&&!/kbList|TaskActivity|kbcontent|星期一/.test(t||'');}
async function raw(url,o){
 const c=(typeof AbortController!=='undefined')?new AbortController():null; let timer=null, timedOut=false;
 const p=pathOf(url); D.path=p; if(D.tried.length<12)D.tried.push((o.method||'GET')+' '+p);
 o.credentials='include'; if(c)o.signal=c.signal;
 const to=new Promise((_,rej)=>{timer=setTimeout(()=>{timedOut=true;try{c&&c.abort()}catch(e){}rej(new Error('timeout '+p));},TMO);});
 try{const r=await Promise.race([fetch(url,o),to]);D.http=r.status;if(r.redirected&&/authserver|\/cas\/|login/i.test(r.url||''))D.login=true;return r;}
 catch(e){if(timedOut||(e&&e.name==='AbortError'))throw new Error('timeout '+p);throw e;}
 finally{clearTimeout(timer);}
}
async function req(url,body){const o={method:body==null?'GET':'POST'};if(body!=null){o.headers={'x-requested-with':'XMLHttpRequest','content-type':'application/x-www-form-urlencoded; charset=UTF-8'};o.body=body;}const r=await raw(url,o);if(!r.ok)throw new Error('HTTP '+r.status);const t=await r.text();if(looksLogin(t))D.login=true;return t;}
async function reqDecoded(url){const r=await raw(url,{method:'GET'});if(!r.ok)throw new Error('HTTP '+r.status);const buf=await r.arrayBuffer();const ct=(r.headers.get('content-type')||'').toLowerCase();let cs=(ct.match(/charset=([\w-]+)/)||[])[1];let t;if(!cs){t=new TextDecoder('utf-8').decode(buf);if(t.indexOf('\ufffd')>=0){try{t=new TextDecoder('gbk').decode(buf);}catch(e){}}}else{try{t=new TextDecoder(cs).decode(buf);}catch(e){t=new TextDecoder('utf-8').decode(buf);}}if(looksLogin(t))D.login=true;return t;}
function enc(o){return Object.keys(o).map(k=>encodeURIComponent(k)+'='+encodeURIComponent(o[k])).join('&');}
function val(id){for(const d of docs()){try{const e=d.getElementById(id);if(e&&e.value)return e.value;}catch(x){}}return null;}
function pageLogin(){for(const d of docs()){try{for(const e of d.querySelectorAll('input[type=password]')){if(e.offsetParent!==null||e.getClientRects===undefined||e.getClientRects().length)return true;}}catch(x){}}return false;}
function nowTerm(){const n=new Date();const y=n.getFullYear(),m=n.getMonth()+1;return {y:m>=8?y:y-1,first:(m>=8||m<=1)};}
try{
 S('page'); D.loginPage=pageLogin();
 if(K==='wisedu'){
  // 金智（东大）：请求与参数和以前完全一样，只加了超时和步骤
  let pre=base('/jwapp/');
  if(pre==null){ if(location.host.indexOf('webvpn')>=0) throw new Error('jwapp'); pre=location.origin; }
  S('term');
  const tj=JSON.parse(await req(pre+'/jwapp/sys/jwpubapp/modules/gg/cxmrxnxq.do','CSDM=SYS&ZCSDM=DQXNXQDM&SFSY=1'));
  const row=(((tj.datas||{}).cxmrxnxq||{}).rows||[])[0]; if(!row) throw new Error('term');
  const xn=row.XNXQDM; let campus=ARG;
  if(campus==null){
   S('campus');
   let cs=[]; try{const cj=JSON.parse(await req(pre+'/jwapp/sys/kbapp/api/wdkbcx/getMyScheduledCampus.do',enc({XNXQDM:xn})));cs=((cj.datas||{}).getMyScheduledCampus)||[];}catch(e){}
   if(cs.length>1){P({ok:true,need:'campus',term:row.XNXQMC||xn,campuses:cs.map(c=>({id:String(c.id),name:String(c.name)}))});return;}
   campus=cs.length?String(cs[0].id):'';
  }
  S('schedule');
  const b={XNXQDM:xn}; if(campus) b.XQDM=campus;
  const dj=await req(pre+'/jwapp/sys/kbapp/api/wdkbcx/getMyScheduleDetail.do',enc(b));
  D.found=/courseName|KCM/.test(dj);
  P({ok:true,kind:K,term:row.XNXQMC||xn,termRow:row,json:dj}); return;
 }
 if(K==='zf'){
  let b=base('/jwglxt/'); b=b!=null?b+'/jwglxt':(base('/kbcx/')||base('/xtgl/')||location.origin);
  const hs=htmls();
  S('term');
  let xnm=val('xnm')||val('xnm_hide'), xqm=val('xqm')||val('xqm_hide'), xsdm=val('xsdm')||'';
  if(!xnm||!xqm){
   // 不在课表页：取课表查询页，读默认选中的学年 / 学期
   try{const t=await req(b+'/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=N2151&layout=default',null);hs.push(t);
    const sel=id=>{const m=t.match(new RegExp('id=["\']'+id+'["\'][\\s\\S]*?</select>'));if(!m)return null;const o=m[0].match(/<option[^>]*value=["'](\d+)["'][^>]*selected/i)||m[0].match(/<option[^>]*selected[^>]*value=["'](\d+)["']/i);return o?o[1]:null;};
    xnm=xnm||sel('xnm'); xqm=xqm||sel('xqm'); const xs=t.match(/id=["']xsdm["'][^>]*value=["']([^"']*)["']/); if(xs)xsdm=xsdm||xs[1];
   }catch(e){}
  }
  const tm=nowTerm(); xnm=xnm||String(tm.y); xqm=xqm||(tm.first?'3':'12');
  S('schedule');
  const ok=t=>{try{const j=JSON.parse(t);return ((j.kbList||(j.data||{}).kbList)||[]).length>0;}catch(e){return false;}};
  let json=null;
  const tries=[
   [b+'/kbcx/xskbcx_cxXsKb.html?gnmkdm=N2151',{xnm:xnm,xqm:xqm,kzlx:'ck'}],
   // 新正方 V9（中国矿业大学等）：cxXsgrkb + kzlx=ck + xsdm
   [b+'/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151',{xnm:xnm,xqm:xqm,kzlx:'ck',xsdm:xsdm}],
   // 有的学校只开了这个（baoozak/timetable zhengfang_new.js）
   [b+'/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N253508',{xnm:xnm,xqm:xqm}],
  ];
  let firstErr=null;
  for(const [u,body] of tries){try{const t=await req(u,enc(body));if(ok(t)){json=t;break;}if(!json&&t.trim().charAt(0)==='{')json=t;}catch(e){firstErr=firstErr||e;}}
  D.found=!!json&&ok(json); D.term=xnm+'-'+xqm;
  if(!json&&firstErr)D.err=String(firstErr.message||firstErr).slice(0,80);
  P({ok:true,kind:K,json:json,html:hs}); return;
 }
 if(K==='cx'){
  // 超星：页面上的 xhid / xqdm / 学期下拉框，没有就去课表页拿，再请求 sdpkkbList（shiguang_warehouse chaoxing.js）
  const o=base('/admin/')||location.origin; const pick=(d,id)=>{try{const e=d.getElementById(id);return (e&&e.value)||null;}catch(x){return null;}};
  let xhid=null,xqdm=null,xnxq=null; const hs=htmls();
  for(const d of docs()){xhid=xhid||pick(d,'xhid');xqdm=xqdm||pick(d,'xqdm');xnxq=xnxq||pick(d,'xnxq1');}
  S('term');
  if(!xhid||!xqdm){try{const t=await req(o+'/admin/pkgl/xskb/queryKbForXsd',null);hs.push(t);const d=new DOMParser().parseFromString(t,'text/html');xhid=xhid||pick(d,'xhid');xqdm=xqdm||pick(d,'xqdm');xnxq=xnxq||pick(d,'xnxq1');}catch(e){}}
  if(!xnxq){const n=new Date(),y=n.getFullYear(),m=n.getMonth()+1;xnxq=m>=8?y+'-'+(y+1)+'-1':(m<=1?(y-1)+'-'+y+'-1':(y-1)+'-'+y+'-2');}
  S('schedule');
  let json=null; if(xhid&&xqdm){try{json=await req(o+'/admin/xsd/pkgl/xskb/sdpkkbList?'+enc({xnxq:xnxq,xhid:xhid,xqdm:xqdm,xskbxslx:'0'}),null);}catch(e){}}
  D.found=!!json&&json.indexOf('kcmc')>=0;
  P({ok:true,kind:K,json:json,html:hs}); return;
 }
 if(K==='urp'){
  // 旧 URP：当前页没有选课列表就去取「本学期课表」（页面常是 GBK）
  const hs=htmls(); S('schedule');
  if(!hs.some(h=>h.indexOf('课程名')>=0)){ const b=base('/xkAction.do')||base('/menu/')||location.origin; try{const t=await reqDecoded(b+'/xkAction.do?actionType=6');hs.push(t);D.found=t.indexOf('课程名')>=0;}catch(e){} } else D.found=true;
  P({ok:true,kind:K,html:hs}); return;
 }
 if(K==='qz'){
  const hs=htmls(); S('schedule');
  if(!hs.some(h=>h.indexOf('kbcontent')>=0)){ const b=(base('/jsxsd')||location.origin)+'/jsxsd/xskb/xskb_list.do'; try{const t=await req(b,null);hs.push(t);D.found=t.indexOf('kbcontent')>=0;}catch(e){} } else D.found=true;
  P({ok:true,kind:K,html:hs}); return;
 }
 if(K==='urpnew'){
  const b=base('/student/')||location.origin; let json=null; const hs=htmls(); S('schedule');
  for(const p of ['/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/callback','/student/courseSelect/thisSemesterCurriculum/ajaxStudentSchedule/curr/callback']){
   try{const t=await req(b+p,'');if(t.trim().charAt(0)==='{'){json=t;break;}}catch(e){}
  }
  if(!json&&!hs.some(h=>h.indexOf('courseTable')>=0)){try{hs.push(await req(b+'/student/courseSelect/thisSemesterCurriculum/index',null));}catch(e){}}
  D.found=!!json;
  P({ok:true,kind:K,json:json,html:hs}); return;
 }
 if(K==='kingo'){
  // 青果：当前页（含 frame）没有课表就试接口。新版 wsxk 接口来自 shiguang_warehouse resources/HIST（MIT）；旧版 jwweb 报表页。都没真实账号验证过。
  const hs=htmls(); S('schedule');
  const has=t=>t&&t.indexOf('星期一')>=0&&t.indexOf('[')>=0;
  if(hs.some(has)) D.found=true;
  else {
   const tm=nowTerm(); const o=base('/jwweb/')||location.origin;
   const urls=[o+'/wsxk/xkjg.ckdgxsxdkchj_data10319.jsp?params='+encodeURIComponent(btoa('xn='+tm.y+'&xq='+(tm.first?0:1))),
     (base('/jwweb/')?base('/jwweb/')+'/jwweb':o)+'/znpk/Pri_StuSel_rpt.aspx?'+enc({Sel_XNXQ:String(tm.y)+(tm.first?'0':'1'),rad:'on',px:'0'})];
   for(const u of urls){try{const t=await reqDecoded(u);hs.push(t);if(has(t)){D.found=true;break;}}catch(e){}}
  }
  P({ok:true,kind:K,html:hs}); return;
 }
 if(K==='eams'){
  // 树维 eams：不靠用户点菜单（WebView 里旧版首页常只剩空架子）。直接带着登录态请求课表接口：
  // courseTableForStd.action 拿 ids / 学期 id → courseTableForStd!courseTable.action（ignoreHead=1）→ 里面的 new TaskActivity(...)。
  const hs=htmls(); const has=t=>!!t&&/new\s+TaskActivity\s*\(/.test(t);
  if(hs.some(has)){D.found=true;P({ok:true,kind:K,html:hs});return;}
  let b=base('/eams/'); b=b!=null?b+'/eams':(base('/courseTableForStd')||base('/home')||location.origin);
  const pickIds=t=>{let m=t.match(/addInput\(\s*form\s*,\s*["']ids["']\s*,\s*["'](\d+)["']/)||t.match(/name=["']ids["'][^>]*value=["'](\d+)["']/)||t.match(/value=["'](\d+)["'][^>]*name=["']ids["']/)||t.match(/[?&]ids=(\d+)/);return m?m[1]:null;};
  const pickSem=t=>{let m=t.match(/name=["']semester\.id["'][^>]*value=["'](\d+)["']/)||t.match(/value=["'](\d+)["'][^>]*name=["']semester\.id["']/)||t.match(/semesterCalendar\(\s*\{[^}]*value\s*:\s*["']?(\d+)/)||t.match(/["']semester\.id["']\s*,\s*["']?(\d+)/)||t.match(/semester\.id=(\d+)/);return m?m[1]:null;};
  const cookieSem=()=>{const m=(document.cookie||'').match(/(?:^|;\s*)semester\.id=(\d+)/);return m?m[1]:null;};
  let ids=null,sem=null,page='';
  for(const h of hs){ids=ids||pickIds(h);sem=sem||pickSem(h);}
  S('term');
  if(!ids||!sem){for(const p of ['/courseTableForStd.action','/courseTableForStd!index.action']){try{page=await req(b+p,null);ids=ids||pickIds(page);sem=sem||pickSem(page);if(ids&&sem)break;}catch(e){}}}
  sem=sem||cookieSem();
  const sems=[]; if(sem)sems.push(sem);
  // 电子科大：学期 id 有规律（2025-2026 第一学期 = 483，每学期 +20）。读不到时按日期推当前和前后学期。
  if(location.host.indexOf('uestc.edu.cn')>=0){const tm=nowTerm();const id=483+(tm.y-2025)*40+(tm.first?0:20);for(const x of [id,id-20,id+20])if(sems.indexOf(String(x))<0)sems.push(String(x));}
  if(!sems.length)sems.push('');
  S('schedule');
  let got=null;
  outer: for(const s of sems){
   for(const withIds of (ids?[true,false]:[false])){
    const o={'ignoreHead':'1','setting.kind':'std','startWeek':'','project.id':'1'}; if(s)o['semester.id']=s; if(withIds)o.ids=ids;
    const q=enc(o);
    try{const t=await req(b+'/courseTableForStd!courseTable.action',q);if(has(t)){got=t;break outer;}}catch(e){}
    try{const t=await req(b+'/courseTableForStd!courseTable.action?'+q,null);if(has(t)){got=t;break outer;}}catch(e){}
   }
  }
  D.found=!!got; D.term=sem||'';
  if(got)hs.push(got); else if(page)hs.push(page);
  P({ok:true,kind:K,html:hs}); return;
 }
 // 其他（乘方、南软、优慕课、从当前页面识别）：读当前页面和里面嵌的 frame
 S('page');
 const hs=htmls(); D.found=hs.some(h=>/星期一|周一|Monday/.test(h)&&/<table/i.test(h));
 P({ok:true,kind:K,html:hs});
}catch(e){P({ok:false,err:String(e&&e.message||e)});}
})();
"""
}

/** 导入结果判断与诊断信息（纯函数，单测）。 */
object JwOutcome {
    /** 失败在哪一步：null = 成功。login 登录未完成 / notfound 没找到课表 / empty 解析为空 / network / timeout / nojwapp / error。 */
    fun failStep(ok: Boolean, err: String, diag: JSONObject?, parsed: Int): String? {
        val login = diag?.optBoolean("login") == true || diag?.optBoolean("loginPage") == true
        if (!ok) return when {
            err.startsWith("timeout") -> "timeout"
            err == "jwapp" -> "nojwapp"
            err == "term" || err.contains("JSON") || err.contains("Unexpected token") || err.contains("HTTP 401") || login -> "login"
            err.contains("HTTP") || err.contains("Failed to fetch") || err.contains("NetworkError") || err.contains("Load failed") -> "network"
            else -> "error"
        }
        if (parsed > 0) return null
        return when {
            diag?.optBoolean("found") == true -> "empty"
            login -> "login"
            else -> "notfound"
        }
    }

    /** 步骤名（界面上显示）。 */
    fun stepLabel(s: String): String = when (s) {
        "start", "page" -> "读取页面"
        "term" -> "读取学期"
        "campus" -> "读取校区"
        "schedule" -> "读取课表"
        "parse" -> "解析课表"
        else -> s
    }

    private val longNum = Regex("\\d{6,}")
    /** 去掉可能是学号 / 工号的长数字，截短。 */
    fun scrub(s: String, max: Int = 90): String = longNum.replace(s.substringBefore('?'), "#").take(max)

    /**
     * 诊断信息：只含版本、教务类型、网址主机名、出错步骤、HTTP 状态码、请求过的接口路径（不带参数）、是否被跳到登录页。
     * 不含 Cookie、账号、密码、学号、课表内容、完整网址。
     */
    fun diagText(version: String, kind: String, host: String, step: String?, fail: String, err: String, diag: JSONObject?, htmlCount: Int, hasJson: Boolean, ua: String, time: String): String {
        val tried = diag?.optJSONArray("tried")?.let { a -> (0 until a.length()).map { scrub(a.optString(it)) } } ?: emptyList()
        return buildString {
            appendLine("知时教务导入诊断")
            appendLine("版本: $version")
            appendLine("类型: $kind")
            appendLine("网址: $host")
            appendLine("出错步骤: ${step ?: diag?.optString("step") ?: "?"}")
            appendLine("结果: $fail")
            if (err.isNotBlank()) appendLine("错误: ${scrub(err, 120)}")
            diag?.let { d ->
                if (!d.isNull("http")) appendLine("HTTP: ${d.optInt("http")}")
                appendLine("被跳到登录页: ${if (d.optBoolean("login")) "是" else "否"}；页面上有密码框: ${if (d.optBoolean("loginPage")) "是" else "否"}")
                appendLine("拿到课表数据: ${if (d.optBoolean("found")) "是" else "否"}")
                if (d.optString("term").isNotBlank()) appendLine("学期参数: ${scrub(d.optString("term"), 20)}")
            }
            if (tried.isNotEmpty()) appendLine("请求过: " + tried.joinToString("；"))
            appendLine("页面数: $htmlCount；接口数据: ${if (hasJson) "有" else "无"}")
            appendLine("浏览器: $ua")
            append("时间: $time")
        }
    }

    /** 浏览器描述：手机版 / 电脑版 + Chrome 主版本（不带设备型号）。 */
    fun uaLabel(ua: String, desktop: Boolean): String =
        (if (desktop) "电脑版" else "手机版") + " Chrome " + (Regex("Chrome/(\\d+)").find(ua)?.groupValues?.get(1) ?: "?")
}
