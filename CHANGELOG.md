# 更新记录 / Changelog

[中文说明](README.md) · [English README](README.en.md)

这里保存版本演进记录。安装、配置和当前使用方式请看 README；旧版本条目不代表当前行为。
This file preserves release history. See the README for installation, configuration, and current behavior; historical entries may describe behavior that has since changed.

## Release overview

| Version | Summary | Detailed record (Chinese) |
| --- | --- | --- |
| 0.1.3-alpha1 | Continuous queue replenishment, recoverable read retries, clearer waiting/error states and restart recovery. Version code 4. | [0.1.3](design/CHANGELOG-0.1.3.md) |
| 0.1.2-alpha1 | Separate pause/resume from a fresh scan; bind video, subtitles and summaries; isolate the source viewer; safely handle legacy summaries. Version code 3. | [0.1.2](design/CHANGELOG-0.1.2.md) |
| 0.1.1-alpha1 | Login return flow, subtitle URL handling, account-state refresh and runtime diagnostics. Version code 2. | [0.1.1](design/CHANGELOG-0.1.1.md) |

The original Chinese release notes below were moved from README without removing their validation caveats. They are historical test reports, not a new test run.

---

## 0.1.3：点一次，持续维护待播（本次更新）

- **移除「连续 3 批没结果就结束」的错误产品规则**。连续模式没有空结果批数/请求次数配额：待播满了等待，消费后自动补；无匹配/无字幕/已看过均继续翻页。
- 前 4 批空结果继续找；从第 5 批起只做 5/10/20/40/60 秒的渐进等待，后续最多等待 60 秒再自动继续，成功即重置。等待不调用模型，不需要手动重开。
- 临时网络超时/断连、HTTP 408/5xx 等 B 站只读请求，在原读取位置按 5 秒至 2 分钟的间隔自动重试，不重做此前的付费排名。认证/风控/证书/配置错误和不确定是否已计费的模型 POST 不盲目自动重发。
- 需要处理时明确显示原因，提供登录/接口设置和 **保留队列重试补货**；不是诱导重新刷而丢掉已准备内容。只有手动提炼指定一条视频是有限任务。
- 当前播报优先展示；运行卡显示待播缺口与自动继续倒计时，详细诊断可展开。倒计时只更新本地页面，不轮询后台或模型。
- 队列空时点击播放/下一条，会等待新内容，不隐式重播历史；可取消等待。已经关闭自动播放时，手动要求「准备好后播放」只代表这次等待，不修改设置。
- 保存的新偏好尚未应用时，明确提示「当前刷取尚未应用」。重新刷之前确认清队列；可以从正在运行的任务直接切换，先取消旧请求再开启新会话，当前播报与收藏不受影响。
- 前台补货意图在普通等待期间保留，避免把“等下一批”当成服务结束。系统后台超时显示真实原因；进程被终止后保留待播并提供继续入口，**不在重启时偷偷自动发付费请求**。不能承诺越过 Android 后台限制。
- 同包名、同签名，版本码 4；覆盖安装，不要先卸载。详细检查见 `design/CHANGELOG-0.1.3.md`。

持续查找遇到新候选仍可能产生筛选费用；采用队列背压与空结果降频，不用隐蔽配额打断你。需要停止费用时可主动暂停刷取。没有降低兴趣阈值，也不以不相关内容凑满队列。

## 0.1.2 修复（上一版）

- **重新刷视频清空旧待播；暂停/继续保留待播。** 保存兴趣本身不清空。新一轮刷取读取最新偏好，旧待播只移到历史；当前播报与收藏保留。此前因旧偏好拒绝的候选可重新参加筛选。没有新增请求配额。
- 增加「暂停刷取 / 继续刷取」，与播放器的暂停独立。暂停取消在途请求，拒绝晚返回结果；等取消完成后可继续或重新刷。服务商已经开始的请求仍可能计费。
- 详情 BVID/AID、首分P CID、player 身份、字幕轨道、摘要和入队进行来源绑定。缺失或冲突时跳过/阻止；标题与作者使用同一份已绑定详情，而不是把候选标题与任意字幕组合。
- 摘要原调用中增加主题一致性和来源 ID 检查，不额外增加一次模型调用。不同视频复用相同字幕资源/正文会保守拦截；分页旧缓存不能覆盖原生返回的最新摘要。
- 原视频改为独立查看页，不调用系统默认 B 站深链，不复用登录成功自动返回逻辑，不允许网页自行唤起外部应用或关闭查看页。保留第 1 分P/时间点；右上角可手动选择浏览器。网页权限/完整版限制仍由 B 站决定。
- 升级后旧版待播因缺少来源绑定，会移至历史并禁止播报，**不删除收藏，不自动付费重提炼**。可在详情手动「重新提炼」，先确认费用，成功替换摘要并保留原记录 ID 和收藏；失败保留旧内容。
- 包名/签名不变，版本码 3。**请直接覆盖安装，不要先卸载。**

来源指纹只能验证本应用取数/传递的一致性；字幕 JSON 通常不自带视频 ID，无法独立证明平台字幕本身绝不标错，模型主题判断也不是事实核查。未获得用户手机日志，不能声称已复现手机上的原始错配或外跳瞬返原因。详见 `design/CHANGELOG-0.1.2.md`。

## 0.1.1 修复（可覆盖安装）

- 使用与 0.1.0 相同的包名和签名，版本码升为 2。直接安装更新，不要先卸载，以保留 Key、设置、历史和收藏。
- 验证码登录后自动检测 Cookie 和账号，成功即返回；只在登录页前台检查本地 Cookie，未检测到登录凭据时不发网络验证。同一登录态不自动重复请求，手动按钮可兜底重试。
- 账号卡片与表单草稿分离，登录/退出立即刷新用户名与按钮，不需要保存，也不覆盖未保存的兴趣、待播数量或输入焦点。
- 字幕支持 HTTP 升级、`//` 地址、`subtitle_url_v2`、空地址跳过、可用语言轨道选择和受限 CDN 跳转；不把 Cookie 发给字幕 CDN，不显示含签名的完整地址。
- 单条字幕资源不可用会跳过，不把整个生产链误报成账号失败；认证/风控/网络异常仍明确停止。
- 待听增加运行状态面板：当前阶段、步骤耗时、视频、获取/筛选/去重/提炼计数、字幕来源和最近跳过原因。
- 播放提示不再覆盖刷取错误；已经停止时不会继续显示成「等待补货」。

真实账号的自动返回、你遇到的具体字幕链接仍需要升级后真机复验。新增回归测试使用替身，没有调用真实模型。
