file_links = [
    {
        "title": "Attention is All you Need",
        "url": "https://proceedings.neurips.cc/paper_files/paper/2017/file/3f5ee243547dee91fbd053c1c4a845aa-Paper.pdf",
    },
    {
        "title": "QLoRA: Efficient Finetuning of Quantized LLMs",
        "url": "https://proceedings.neurips.cc/paper_files/paper/2023/file/1feb87871436031bdc0f2beaa62a049b-Paper-Conference.pdf",
    },
]

import os
import wget


def is_exist(file_links):
    return os.path.exists(f"./generative_ai/{file_links['title']}.pdf")


for file_link in file_links:
    if not is_exist(file_links):
        wget.download(file_link["url"], out=f"./generative_ai/{file_link["title"]}.pdf")
