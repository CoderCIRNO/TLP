# TLP (Time-lapse photography)

### *本项目尚为半成品，目前仅在小米11上通过测试*

## 延时摄影

**延时摄影**（Time-lapse photography），又叫缩时摄影、缩时录影，是以一种将时间压缩的拍摄技术。其拍摄的通常是一组照片，后期通过将照片串联合成视频，把几分钟、几小时甚至是几天的过程压缩在一个较短的时间内以视频的方式播放。在一段延时摄影视频中，物体或者景物缓慢变化的过程被压缩到一个较短的时间内，呈现出平时用肉眼无法察觉的奇异精彩的景象。

## 初衷

最初只是为了把淘汰下来放在家里吃灰的Redmi Note8利用起来，便买了一个手机三脚架，在上班期间用MIUI相机的延时摄影功能拍摄高层出租屋窗外的景色。

为了尽量延长拍摄时间，我把后台应用清空，屏幕亮度调到最低，并部署了一个充电宝以保持供电，结果下班回家发现手机只工作了七个小时就没电了，导致没能拍到当天傍晚珍贵的双彩虹画面。

事后我查看了手机耗电情况，原本预计手机过早关机是因为屏幕耗电过多，但耗电统计显示相机app耗电远超屏幕。恰巧最近入职培训在了解Camera 2 API，种种因素的影响下便萌生了自己写一个延时摄影专用app的想法。

## 优化方向

MIUI相机在延时摄影拍摄过程中会显示预览画面，意味着相机会setRepeatRequest，虽未经过实际测试，但这一过程连续不断的Capture以及频繁的3A调整应当是相当耗电的。我认为预览画面在长时间长拍摄间隔的延时摄影中必要性并不大，因此我在本程序中取消了拍摄中的预览画面，取而代之的是显示目前拍摄了多少帧画面，但保留了拍摄前的预览以便用户取景、确定相机位置。实践证明这一改动是有成效的，但仅达到这个程度还不能满足我的需求。

曾使用MIUI相机的延时摄影功能拍摄过日出的过程，从黑夜到白天的时候，视频画面因过曝一片惨白，原因不明。针对自己的使用需求，我在本程序中关闭了AF，焦距设置为无限远；打开了AE和AWB，经测试没有出现类似的问题。

## API使用

摄像部分使用了 Camera 2 API

视频合成使用 MediaRecorder

## TODO

- [ ] **实现灭屏拍摄**

  目前程序灭屏后便会停止工作，而且程序没有设置维持亮屏……只能在开发者选项中选择维持亮屏的选项。

- [ ] **提高兼容性**

  程序经过测试在**小米11国际版**上能够正常工作；

  在**Redmi Note8**上结束拍摄时会闪退，且视频输出不能正常播放；

  在**小米11青春版**上无法开始拍摄，其他机型未做测试。

  **21.9.3更新-原因大概是MediaRecorder设置的视频尺寸太大，而MediaRecorder无法处理尺寸很大的视频，无法完成prepare。因此不能直接取主摄最大尺寸，应当做一些权衡或者直接换其他方式处理视频**

- [ ] **视频合成优化**

  经过实际拍摄，成片相当模糊，质量并不高。

  具体原因尚待测试，但推测是视频压缩的原因。

- [ ] **结束拍摄的等待优化**

  目前结束拍摄时会向子线程发送一个message告诉子线程可以结束拍摄，但子线程的拍摄间隔控制是用sleep实现的，因此子线程只有在sleep完成后才能处理message，结束拍摄。往往我会把拍摄间隔设置为60秒，接近一分钟的等待时间还是比较漫长的，因此这部分需要优化。

- [ ] **增加自动手动模式的切换**

  此程序目前只满足了有限的使用场景，如果有用户要拍摄盆栽，则会因为程序焦距锁定无限远而无法满足。因此对3A参数应当分别设置手动和自动的切换。

- [ ] **拍摄间隔设置滑动条优化**

  拍摄间隔比较难设置，可以参考MIUI专业模式曝光时间设置滑动条左疏右密的设计。
