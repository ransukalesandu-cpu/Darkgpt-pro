import sqlite3
import streamlit as st

from utils.get_response import (
    display_model_mapping,
    get_bot_response,
    get_model,
    get_provider,
)

st.set_page_config(
    page_title="DarkGPT",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded",
)


def local_css(file_name):
    with open(file_name, encoding="utf-8") as css_file:
        st.markdown(f"<style>{css_file.read()}</style>", unsafe_allow_html=True)


local_css("style.css")


def main():
    try:
        if "chat_history" not in st.session_state:
            st.session_state.chat_history = []
        if "conversation_id" not in st.session_state:
            st.session_state.conversation_id = 1

        col1, col2, col3 = st.columns(3)
        with col1:
            st.page_link("app.py", label="Back to Home", icon="🏠")
            st.page_link("pages/summarize.py", label="Summarize", icon="📝")
        with col2:
            st.header("DarkGPT")
        with col3:
            display_model = st.selectbox("Select Model", list(display_model_mapping))
            internal_model = get_model(display_model)
            provider_name = get_provider(internal_model)

        if st.sidebar.button("✨ New Chat"):
            st.session_state.chat_history.clear()
            st.session_state.conversation_id += 1

        st.sidebar.write("Chat History")
        try:
            with sqlite3.connect("chat_history.db") as conn:
                conn.execute(
                    "CREATE TABLE IF NOT EXISTS chat_history "
                    "(conversation_id INTEGER, role TEXT, content TEXT)"
                )
                rows = conn.execute(
                    "SELECT DISTINCT conversation_id FROM chat_history ORDER BY conversation_id DESC"
                ).fetchall()
                for (conv_id,) in rows:
                    if st.sidebar.button(f"Conversation {conv_id}", key=f"history_{conv_id}"):
                        st.session_state.conversation_id = conv_id
                        st.session_state.chat_history = [
                            {"role": role, "content": content}
                            for role, content in conn.execute(
                                "SELECT role, content FROM chat_history WHERE conversation_id=?",
                                (conv_id,),
                            ).fetchall()
                        ]
        except sqlite3.Error as exc:
            st.sidebar.warning(f"Chat history unavailable: {exc}")

        if st.sidebar.button("Clear Chat History ✖️"):
            st.session_state.chat_history.clear()
            with sqlite3.connect("chat_history.db") as conn:
                conn.execute("DELETE FROM chat_history")
                conn.commit()

        for chat in st.session_state.chat_history:
            role = "assistant" if chat["role"] == "bot" else chat["role"]
            with st.chat_message(role):
                st.markdown(chat["content"])

        user_input = st.chat_input("Ask Anything ...")
        if user_input:
            with st.chat_message("user"):
                st.markdown(user_input)
            try:
                bot_response = get_bot_response(user_input, internal_model, provider_name)
                st.session_state.chat_history.extend(
                    [
                        {"role": "user", "content": user_input},
                        {"role": "bot", "content": bot_response},
                    ]
                )
                with st.chat_message("assistant"):
                    st.markdown(bot_response)
                with sqlite3.connect("chat_history.db") as conn:
                    conn.executemany(
                        "INSERT INTO chat_history VALUES (?, ?, ?)",
                        [
                            (st.session_state.conversation_id, "user", user_input),
                            (st.session_state.conversation_id, "bot", bot_response),
                        ],
                    )
                    conn.commit()
            except Exception as exc:
                st.error(f"An error occurred while generating a response: {exc}")
    except Exception as exc:
        st.error(f"An error occurred: {exc}")


if __name__ == "__main__":
    main()
