export type VariableType =
  | "TEXT"
  | "LONG_TEXT"
  | "NUMBER"
  | "DECIMAL"
  | "DATE"
  | "DATETIME"
  | "BOOLEAN"
  | "EMAIL"
  | "PHONE"
  | "SELECT"
  | "MULTISELECT"
  | "CURRENCY";

export interface FormFieldConstraints {
  minLength?: number | null;
  maxLength?: number | null;
  min?: number | null;
  max?: number | null;
  pattern?: string | null;
  options?: string[];
}

export interface FormFieldSchema {
  key: string;
  label: string;
  type: VariableType;
  required: boolean;
  defaultValue?: string | null;
  placeholder?: string | null;
  displayOrder: number;
  component: string;
  validation: FormFieldConstraints;
  aiEnabled?: boolean;
  aiMode?: string | null;
}

export interface FormSchema {
  templateVersionId: string;
  templateId: string;
  templateCode: string;
  templateName: string;
  versionNumber: number;
  fields: FormFieldSchema[];
}

export interface TemplateSummary {
  id: string;
  code: string;
  name: string;
  status: string;
  origin?: string | null;
  sourcePackId?: string | null;
  currentVersionNumber?: number | null;
  currentVersionId?: string | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string | null;
}

export interface FieldErrorDetail {
  field: string;
  message: string;
}

export interface ErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  details: FieldErrorDetail[];
  traceId?: string | null;
}