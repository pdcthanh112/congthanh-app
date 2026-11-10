export type Category = CategoryModel | CategoryJson;

type CategoryJson = {
  id: string
  name: string
  slug: string
  description: string
  children: CategoryJson[]
}

type CategoryModel = {
  id: string
  name: string
  slug: string
  description: string
  parentId: string
}
