package com.aman.gigi.utils

import com.aman.gigi.BuildConfig

class Constants {
    companion object {
        const val COMMAND_SET_RELATIONSHIP_TYPE = "set_relationship_type"

        const val COMMAND_SEND_QUOTE = "send_quote"
        const val COMMAND_LOVE_CARD_STACK = "love_card_stack"
        const val COMMAND_LOVE_CARD_OPENED = "love_card_opened"
        const val COMMAND_LOVE_CARD_ANSWERED = "love_card_answered"
        const val COMMAND_PARTNER_PROFILE_UPDATED = "partner_profile_updated"
        const val COMMAND_EXCHANGE_MUSIC_HISTORY = "exchange_music_history"
        const val COMMAND_POKE = "poke"

        const val COMMAND_GROUP_NAME_CHANGED = "group_name_changed"
        const val COMMAND_MEMBER_REMOVED = "member_removed"
        
        const val REMINDER_ITEM_KEY = "reminder_item"
        const val SERVER_URL = BuildConfig.SERVER_URL
    }
}
