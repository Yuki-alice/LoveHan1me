package lovehan1me.ui.viewmodel
import lovehan1me.logic.ioDispatcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import lovehan1me.logic.DatabaseRepo
import lovehan1me.data.database.entity.HKeyframeEntity
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/07/01 001 13:40
 */
class SettingsViewModel : ViewModel() {

    fun loadAllHKeyframes(keyword: String? = null) =
        DatabaseRepo.HKeyframe.loadAll(keyword).flowOn(ioDispatcher)

    fun deleteHKeyframes(entity: HKeyframeEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.delete(entity)
        }
    }

    fun updateHKeyframes(entity: HKeyframeEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.update(entity)
        }
    }

    fun removeHKeyframe(videoCode: String, keyframe: HKeyframeEntity.Keyframe) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.removeKeyframe(videoCode, keyframe)
        }
    }


    fun modifyHKeyframe(
        videoCode: String,
        oldKeyframe: HKeyframeEntity.Keyframe, keyframe: HKeyframeEntity.Keyframe,
    ) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.modifyKeyframe(videoCode, oldKeyframe, keyframe)
        }
    }

    fun insertHKeyframes(entity: HKeyframeEntity) {
        viewModelScope.launch(ioDispatcher) {
            DatabaseRepo.HKeyframe.insert(entity)
        }
    }

    fun loadAllSharedHKeyframes() =
        DatabaseRepo.HKeyframe.loadAllShared().flowOn(ioDispatcher)
}
