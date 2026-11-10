# pip install psycopg2-binary pymongo

import psycopg2
from psycopg2.extras import RealDictCursor
from pymongo import MongoClient

# Kết nối PostgreSQL
pg_conn = psycopg2.connect(
    host='localhost',
    database='CongThanhApp-Ecommerce',
    user='postgres',
    password='123456',
    port=5432
)

# Kết nối MongoDB
mongo_client = MongoClient('mongodb://localhost:27017/')
mongo_db = mongo_client['CongThanhApp-Ecommerce']
category_collection = mongo_db['category']

def fetch_categories(cursor):
    # Thực thi truy vấn lấy dữ liệu từ PostgreSQL
    cursor.execute("SELECT * FROM category")
    return cursor.fetchall()

def build_hierarchy(categories):
    category_dict = {cat['id']: cat for cat in categories}
    for cat in categories:
        cat['children'] = []
        parent_id = cat['parent_id']
        if parent_id:
            category_dict[parent_id]['children'].append(cat)
    return [cat for cat in categories if not cat['parent_id']]

def migrate_to_mongodb(categories):
    # Chèn dữ liệu vào MongoDB
    category_collection.insert_many(categories)

def main():
    with pg_conn.cursor(cursor_factory=RealDictCursor) as cursor:
        categories = fetch_categories(cursor)
        hierarchical_data = build_hierarchy(categories)
        migrate_to_mongodb(hierarchical_data)
    print("Migration completed!")

if __name__ == "__main__":
    main()


#python -m venv myenv
#.\myenv\Scripts\activate
#pip install psycopg2-binary pymongo
#python sync.py

#deactivate
#rmdir /s /q myenv
