import { z } from "zod";
import type { FormFieldSchema, FormSchema } from "@/api/types";

/** RHF treats `.` as nesting — keep flat keys for template variables like `client.firstName`. */
export function toFormKey(apiKey: string): string {
  return apiKey.replaceAll(".", "__");
}

export function toApiKey(formKey: string): string {
  return formKey.replaceAll("__", ".");
}

export function toApiData(values: Record<string, unknown>): Record<string, unknown> {
  const data: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(values)) {
    data[toApiKey(key)] = value;
  }
  return data;
}

export function toFormData(values: Record<string, unknown>): Record<string, unknown> {
  const data: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(values)) {
    data[toFormKey(key)] = value;
  }
  return data;
}

function fieldSchema(field: FormFieldSchema): z.ZodTypeAny {
  const required = field.required;
  const v = field.validation ?? {};

  switch (field.type) {
    case "EMAIL": {
      let s = z.string().email("Email invalide");
      if (v.minLength) s = s.min(v.minLength);
      if (v.maxLength) s = s.max(v.maxLength);
      return required ? s.min(1, "Champ obligatoire") : s.optional().or(z.literal(""));
    }
    case "PHONE": {
      const s = z.string().regex(/^[+0-9][0-9\s().-]{5,30}$/, "Telephone invalide");
      return required ? s : s.optional().or(z.literal(""));
    }
    case "NUMBER": {
      const n = z.coerce.number({ invalid_type_error: "Nombre invalide" }).int();
      const bounded = n
        .refine((val) => v.min == null || val >= v.min, `Min ${v.min}`)
        .refine((val) => v.max == null || val <= v.max, `Max ${v.max}`);
      return required ? bounded : z.union([bounded, z.nan(), z.literal("")]).optional();
    }
    case "DECIMAL":
    case "CURRENCY": {
      let n = z.coerce.number({ invalid_type_error: "Nombre invalide" });
      if (field.type === "CURRENCY") {
        n = n.min(v.min ?? 0, "Montant >= 0");
      } else if (v.min != null) {
        n = n.min(v.min);
      }
      if (v.max != null) n = n.max(v.max);
      return required ? n : z.union([n, z.nan(), z.literal("")]).optional();
    }
    case "DATE": {
      const s = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, "Date ISO invalide");
      return required ? s : s.optional().or(z.literal(""));
    }
    case "DATETIME": {
      const s = z.string().min(1, "Date-heure requise");
      return required ? s : s.optional().or(z.literal(""));
    }
    case "BOOLEAN":
      return z.boolean();
    case "SELECT": {
      const s = z.string();
      return required ? s.min(1, "Champ obligatoire") : s.optional().or(z.literal(""));
    }
    case "MULTISELECT": {
      const arr = z.array(z.string());
      return required ? arr.min(1, "Champ obligatoire") : arr.optional();
    }
    case "LONG_TEXT":
    case "TEXT":
    default: {
      let s = z.string();
      if (required) s = s.min(1, "Champ obligatoire");
      if (v.minLength) s = s.min(v.minLength, `Min ${v.minLength} caracteres`);
      if (v.maxLength) s = s.max(v.maxLength, `Max ${v.maxLength} caracteres`);
      if (v.pattern) {
        try {
          const re = new RegExp(v.pattern);
          s = s.regex(re, "Format invalide");
        } catch {
          /* ignore bad pattern */
        }
      }
      return required ? s : s.optional().or(z.literal(""));
    }
  }
}

export function schemaToZod(schema: FormSchema) {
  const shape: Record<string, z.ZodTypeAny> = {};
  for (const field of schema.fields) {
    shape[toFormKey(field.key)] = fieldSchema(field);
  }
  return z.object(shape);
}

export function defaultValues(schema: FormSchema): Record<string, unknown> {
  const values: Record<string, unknown> = {};
  for (const field of schema.fields) {
    const key = toFormKey(field.key);
    if (field.type === "BOOLEAN") {
      values[key] = field.defaultValue === "true";
    } else if (field.type === "MULTISELECT") {
      values[key] = field.defaultValue
        ? field.defaultValue.split(",").map((s) => s.trim())
        : [];
    } else {
      values[key] = field.defaultValue ?? "";
    }
  }
  return values;
}
