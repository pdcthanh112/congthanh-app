import { format } from "date-fns"

export const DATETIME_FORMAT = {
  DATE_TIME_FORMAT: 'yyyy-MM-dd HH:mm'
}

export const dateHelper = {
  formatDate: (date: Date | string | number | undefined, formatString: string = DATETIME_FORMAT.DATE_TIME_FORMAT) => {
if(!date) return undefined

return format(new Date(date), formatString)
  }
}