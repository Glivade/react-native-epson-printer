export enum InterfaceType {
  LAN = "LAN",
  Bluetooth = "Bluetooth"
}

export interface DiscoverParams {
  interface_type: InterfaceType;
}

export interface PrintParams {
  printer: PrinterInfo,
  data: String,
  receipt_copy_count: number,
  font_size?: FontSize,
}

export interface PrinterInfo {
  name: string;
  interface_type: string;
  mac_address: string;
  target: string;
}

export enum FontSize {
  Small = "Small",
  Regular = "Regular",
  Medium = "Medium",
  Large = "Large"
}
