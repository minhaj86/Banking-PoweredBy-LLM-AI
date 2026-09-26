export function money(
  value: string | null | undefined,
  currency = "SEK",
): string {
  if (value == null || !/^-?\d+(\.\d+)?$/.test(value)) return "Not available";
  const [whole, decimal = "00"] = value.split(".");
  return `${whole.replace(/\B(?=(\d{3})+(?!\d))/g, "\u202f")}.${decimal.padEnd(2, "0")} ${currency}`;
}
export function percent(rate: string): string {
  if (!/^\d+(\.\d+)?$/.test(rate)) return "Not available";
  const [whole, fraction = ""] = rate.split(".");
  const digits = whole + fraction.padEnd(2, "0");
  const point = whole.length + 2;
  const integer = digits.slice(0, point).replace(/^0+(?=\d)/, "");
  const decimal = digits.slice(point).replace(/0+$/, "");
  return `${integer}${decimal ? "." + decimal : ""}%`;
}
export function timestamp(value: string): string {
  return (
    new Intl.DateTimeFormat("en-GB", {
      dateStyle: "medium",
      timeStyle: "short",
      timeZone: "UTC",
    }).format(new Date(value)) + " UTC"
  );
}
export function words(value: string): string {
  return value
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^\w/, (c) => c.toUpperCase());
}
export const editable = (state: string) =>
  [
    "DRAFT",
    "DOCUMENTS_REQUIRED",
    "MANUAL_REVIEW",
    "ASSESSMENT_FAILED",
  ].includes(state);
