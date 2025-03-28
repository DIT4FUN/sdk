    private fun escapeHtml(content: String): String {
        // 修改双引号转义方式，并移除单引号转义（避免影响JavaScript单引号包裹）
        return content.replace("\"", "\\\"").replace("\n", "<br>")
    }

    private fun updateChatWebView() {
        val htmlContent = messageList.joinToString("") { message ->
            val processedContent = when (message.role) {
                "user" -> "<b>You:</b> ${escapeHtml(message.content)}"
                "assistant" -> "<b>Assistant:</b> ${escapeHtml(message.content)}"
                else -> escapeHtml(message.content) // 保留原有逻辑
            }
            """
                <div style='margin: 4px 0; padding: 4px; border-radius: 8px;'>$processedContent</div>
            """.trimIndent()
        }
        // 修改JavaScript字符串使用单引号包裹以避免双引号冲突
        binding.chatWebView.evaluateJavascript("document.getElementById('chatContainer').innerHTML = '$htmlContent'; setTimeout(() => window.scrollTo(0, document.body.scrollHeight), 0);", null)
    }

    override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
        if (chatResponse.message.content.isNotEmpty()) {
            if (currentMessage == null) {
                // 强制设置role为assistant确保消息显示
                currentMessage = Message(
                    "assistant",
                    chatResponse.message.content,
                    chatResponse.message.images
                )
            } else {
                currentMessage!!.content += chatResponse.message.content
            }
        }
    }