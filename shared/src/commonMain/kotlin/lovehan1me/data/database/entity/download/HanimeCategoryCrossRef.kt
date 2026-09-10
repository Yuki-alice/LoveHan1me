package lovehan1me.data.database.entity.download

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    primaryKeys = ["videoId", "categoryId"],
    indices = [Index(value = ["categoryId"])],
)
data class HanimeCategoryCrossRef(
    val videoId: Int,
    val categoryId: Int,
)
