import streamlit as st
import nltk
from io import StringIO
from nltk.tokenize import word_tokenize, sent_tokenize
import readtime
import textstat
from transformers import pipeline

st.set_page_config(page_title="DarkGPT", page_icon="🤖", layout="wide")


@st.cache_resource
def load_summarizer():
    return pipeline("summarization")


def summarize_text(input_text):
    summarizer = load_summarizer()
    result = summarizer(input_text, max_length=150, min_length=30, do_sample=False)
    return result[0]["summary_text"]


def analyze_text(input_text):
    try:
        nltk.data.find("tokenizers/punkt")
    except LookupError:
        nltk.download("punkt", quiet=True)
    words = word_tokenize(input_text)
    sentences = sent_tokenize(input_text)
    return {
        "reading_time": readtime.of_text(input_text),
        "text_complexity": textstat.flesch_reading_ease(input_text),
        "lexical_richness": len(set(words)) / len(words) if words else 0,
        "num_sentences": len(sentences),
    }


def display_homepage():
    st.title("Welcome to DarkGPT! 🤖")
    st.write("A text assistant for summarization and analysis.")
    st.page_link("app.py", label="Back to Home", icon="🏠")
    st.page_link("pages/DarkGPT.py", label="DarkGPT Chat", icon="💬")
    st.markdown("---")
    st.write("Use the navigation on the sidebar to summarize or analyze text.")


def display_summarization_page():
    st.subheader("Summarize")
    source = st.radio(
        "How would you like to start?",
        ("I want to input some text", "I want to upload a file"),
        key="summary_source",
    )
    example = (
        "Artificial intelligence (AI) is intelligence demonstrated by machines. "
        "AI applications include search engines, recommendation systems, speech "
        "recognition, self-driving cars, and strategic game systems."
    )
    if source == "I want to input some text":
        text = st.text_area("Enter text to summarize", value=example, max_chars=10000)
        if st.button("Summarize"):
            if len(text) < 50:
                st.error("Please enter at least 50 characters.")
            else:
                try:
                    st.success(summarize_text(text))
                except Exception as exc:
                    st.error(f"Summarization failed: {exc}")
    else:
        file = st.file_uploader("Upload a text file", type=["txt"])
        if file is not None:
            text = StringIO(file.getvalue().decode("utf-8")).read()
            if not 50 <= len(text) <= 10000:
                st.error("Please upload a file containing 50–10,000 characters.")
            elif st.button("Summarize uploaded file"):
                try:
                    st.success(summarize_text(text))
                except Exception as exc:
                    st.error(f"Summarization failed: {exc}")


def display_analysis_page():
    st.subheader("Analyze")
    source = st.radio(
        "How would you like to start?",
        ("I want to input some text", "I want to upload a file"),
        key="analysis_source",
    )
    if source == "I want to input some text":
        text = st.text_area("Enter text to analyze", max_chars=10000, key="analysis_text")
    else:
        file = st.file_uploader("Upload a text file", type=["txt"], key="analysis_file")
        text = file.getvalue().decode("utf-8") if file else ""

    if text and st.button("Analyze"):
        try:
            result = analyze_text(text)
            st.write("Reading Time:", result["reading_time"])
            st.write("Text Complexity:", result["text_complexity"])
            st.write("Lexical Richness:", result["lexical_richness"])
            st.write("Number of Sentences:", result["num_sentences"])
        except Exception as exc:
            st.error(f"Analysis failed: {exc}")


def main():
    st.sidebar.header("DarkGPT")
    page = st.sidebar.radio(
        "Navigate",
        ["Homepage", "Summarize text", "Analyze text"],
    )
    if page == "Homepage":
        display_homepage()
    elif page == "Summarize text":
        display_summarization_page()
    else:
        display_analysis_page()


if __name__ == "__main__":
    main()
