import { z } from "zod";
import type { TFunction } from "i18next";
import i18n from "@/i18n";
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

type Translate = TFunction;

function makeTv(t: Translate) {
  return (key: string, opts?: Record<string, unknown>): string =>
    String(t(`validation.${key}`, opts));
}

function fieldSchema(field: FormFieldSchema, tv: (key: string, opts?: Record<string, unknown>) => string): z.ZodTypeAny {
  const required = field.required;
  const v = field.validation ?? {};

  switch (field.type) {
    case "EMAIL": {
      let s = z.string();
      if (required) s = s.min(1, tv("required"));
      s = s.email(tv("email"));
      if (v.minLength) s = s.min(v.minLength, tv("minLength", { min: v.minLength }));
      if (v.maxLength) s = s.max(v.maxLength, tv("maxLength", { max: v.maxLength }));
      return required ? s : s.optional().or(z.literal(""));
    }
    case "PHONE": {
      let s = z.string();
      if (required) s = s.min(1, tv("required"));
      s = s.regex(/^[+0-9][0-9\s().-]{5,30}$/, tv("phone"));
      return required ? s : s.optional().or(z.literal(""));
    }
    case "NUMBER": {
      const n = z.coerce.number({ invalid_type_error: tv("number") }).int(tv("integer"));
      const bounded = n
        .refine((val) => v.min == null || val >= v.min, tv("min", { min: v.min }))
        .refine((val) => v.max == null || val <= v.max, tv("max", { max: v.max }));
      return required ? bounded : z.union([bounded, z.nan(), z.literal("")]).optional();
    }
    case "DECIMAL":
    case "CURRENCY": {
      let n = z.coerce.number({ invalid_type_error: tv("number") });
      if (field.type === "CURRENCY") {
        n = n.min(v.min ?? 0, tv("amountMin", { min: v.min ?? 0 }));
      } else if (v.min != null) {
        n = n.min(v.min, tv("min", { min: v.min }));
      }
      if (v.max != null) n = n.max(v.max, tv("max", { max: v.max }));
      return required ? n : z.union([n, z.nan(), z.literal("")]).optional();
    }
    case "DATE": {
      let s = z.string();
      if (required) s = s.min(1, tv("required"));
      s = s.regex(/^\d{4}-\d{2}-\d{2}$/, tv("date"));
      return required ? s : s.optional().or(z.literal(""));
    }
    case "DATETIME": {
      const s = z.string().min(1, tv(required ? "required" : "datetime"));
      return required ? s : s.optional().or(z.literal(""));
    }
    case "BOOLEAN":
      return z.boolean();
    case "SELECT": {
      const s = z.string();
      return required ? s.min(1, tv("required")) : s.optional().or(z.literal(""));
    }
    case "MULTISELECT": {
      const arr = z.array(z.string());
      return required ? arr.min(1, tv("required")) : arr.optional();
    }
    case "LONG_TEXT":
    case "TEXT":
    default: {
      let s = z.string();
      if (required) s = s.min(1, tv("required"));
      if (v.minLength) s = s.min(v.minLength, tv("minLength", { min: v.minLength }));
      if (v.maxLength) s = s.max(v.maxLength, tv("maxLength", { max: v.maxLength }));
      if (v.pattern) {
        try {
          const re = new RegExp(v.pattern);
          s = s.regex(re, tv("format"));
        } catch {
          /* ignore bad pattern */
        }
      }
      return required ? s : s.optional().or(z.literal(""));
    }
  }
}

export function schemaToZod(schema: FormSchema, t: Translate = i18n.t.bind(i18n)) {
  const tv = makeTv(t);
  const shape: Record<string, z.ZodTypeAny> = {};
  for (const field of schema.fields) {
    shape[toFormKey(field.key)] = fieldSchema(field, tv);
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
