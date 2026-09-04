import type { FormFieldSchema } from "@/api/types";

export interface FieldSection {
  id: string;
  title: string;
  fields: FormFieldSchema[];
}

/** Group fields by dotted key prefix (office.name → "Office"). Single flat list if no dots. */
export function groupFieldsByPrefix(fields: FormFieldSchema[]): FieldSection[] {
  const sorted = [...fields].sort((a, b) => a.displayOrder - b.displayOrder);
  const hasDots = sorted.some((f) => f.key.includes("."));
  if (!hasDots) {
    return [{ id: "fields", title: "Champs", fields: sorted }];
  }

  const order: string[] = [];
  const map = new Map<string, FormFieldSchema[]>();

  for (const field of sorted) {
    const prefix = field.key.includes(".") ? field.key.split(".")[0]! : "autres";
    if (!map.has(prefix)) {
      order.push(prefix);
      map.set(prefix, []);
    }
    map.get(prefix)!.push(field);
  }

  return order.map((prefix) => ({
    id: prefix,
    title: formatSectionTitle(prefix),
    fields: map.get(prefix)!,
  }));
}

function formatSectionTitle(prefix: string): string {
  if (!prefix) return "Champs";
  return prefix
    .replace(/[_-]+/g, " ")
    .replace(/([a-z])([A-Z])/g, "$1 $2")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/** Wide controls that should span the full grid row. */
export function isFullWidthField(field: FormFieldSchema): boolean {
  return field.component === "textarea" || field.component === "multiselect";
}
