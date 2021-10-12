export interface DiscoverParams {
  is_network?: boolean;
  is_bluetooth?: boolean;
}

export interface PrintParams {
  printer: PrinterInfo,
  data: String,
}

export interface PrinterInfo {
  name: string;
  mac: string;
  target: string;
}
