package lovehan1me.core.domain.model

// 这三个模型的真身已下沉到 :video:contract。
// 留别名是因为全仓（含 :shared 的 AppSettings 持久化）都是按本包 import 的，
// 一次性改所有 import 会牵出一堆无关改动，先断依赖方向、包路径下次再收。
public typealias PlayerKernel = lovehan1me.video.contract.PlayerKernel
public typealias VideoAspectMode = lovehan1me.video.contract.VideoAspectMode
public typealias PictureAdjust = lovehan1me.video.contract.PictureAdjust
