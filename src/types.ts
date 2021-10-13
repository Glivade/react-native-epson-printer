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
}

export interface PrinterInfo {
  name: string;
  interface_type: string;
  mac_address: string;
  target: string;
}
