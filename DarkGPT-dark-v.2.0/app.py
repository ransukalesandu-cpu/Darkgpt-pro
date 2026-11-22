import streamlit as st

st.set_page_config(
    page_title="DarkGPT",
    page_icon="🤖",
    layout="wide",
    initial_sidebar_state="expanded",
)


def main():
    st.markdown(
        "<h1 style='text-align:center;color:white;font-size:36px;'>Welcome to DarkGPT! 🧨</h1>",
        unsafe_allow_html=True,
    )
    st.markdown("<h3 style='text-align:center;font-size:56px;'>🤖</h3>", unsafe_allow_html=True)
    st.markdown(
        "<h3 style='text-align:center;color:grey;font-size:20px;'>DarkGPT: Your AI text assistant for quick summarization and analysis.</h3>",
        unsafe_allow_html=True,
    )
    st.write("Choose an option below to get started!")
    col1, col2 = st.columns(2)
    with col1:
        if st.button("DarkGPT"):
            st.switch_page("pages/DarkGPT.py")
    with col2:
        if st.button("Summarize"):
            st.switch_page("pages/summarize.py")
    st.markdown("---")
    st.markdown("### What is DarkGPT?")
    st.write(
        "DarkGPT is a Streamlit text assistant with chat, summarization, "
        "and text-analysis pages."
    )
    st.markdown("### Features")
    st.write(
        "- Multiple AI model options\n"
        "- Chat history\n"
        "- Text summarization\n"
        "- Reading-time and text-complexity analysis"
    )


if __name__ == "__main__":
    main()
