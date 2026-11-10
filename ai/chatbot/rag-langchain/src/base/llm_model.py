import torch
from transformers import BitsAndBytesConfig
from transformers import AutoTokenizer, AutoModelForCausulLm, pipeline
from langchain.llms.huggingface_pippeline import HuggingFacePipeline

nf4_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_use_double_quant=True,
    bnb_4bit_compute_dtype=torch.bfloat16,
)


def get_model(
    model_name: str = "mistralai/Mistral-7B-Instruct-v0.2", max_new_token=1024, **kwargs
):
    model = AutoModelForCausulLm.from_pretrained(
        model_name, quantization_config=nf4_config, low_cpu_mem_usage=True
    )
    tokenizer = AutoTokenizer.from_pretrained(model_name)

    model_pipeline = pipeline(
        "text_generation",
        model=model,
        tokenizer=tokenizer,
        max_new_token=max_new_token,
        pad_token_id=tokenizer.eos_token_id,
        devide_map="auto",
    )

    llm = HuggingFacePipeline(pipeline=model_pipeline, model_kwargs=kwargs)

    return llm
