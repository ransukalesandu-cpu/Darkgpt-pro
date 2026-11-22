import g4f.cookies
from g4f.client import Client

model_provider_mapping = {
    "gpt-3.5-turbo": "DDG",
    "gpt-4o": None,
    "mixtral_8x7b": "HuggingFace",
    "blackbox": "Blackbox",
    "meta": "MetaAI",
    "llama3_70b_instruct": "MetaAI",
}

display_model_mapping = {
    "gpt 3.5 turbo": "gpt-3.5-turbo",
    "gpt 4o": "gpt-4o",
    "llama 3": "llama3_70b_instruct",
    "Mixtral 70b": "mixtral_8x7b",
    "BlackBox": "blackbox",
    "Meta AI": "meta",
}


def get_provider(model: str) -> str:
    return model_provider_mapping.get(model, "")


def get_model(display_model: str) -> str:
    return display_model_mapping.get(display_model, "")


def get_bot_response(prompt, internal_model, provider_name):
    client = Client()
    response = client.chat.completions.create(
        model=internal_model,
        messages=[{"role": "user", "content": prompt}],
        provider=provider_name,
        cookies=g4f.cookies.get_cookies("bing"),
    )
    return response.choices[0].message.content
