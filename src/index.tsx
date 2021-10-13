import {NativeModules, Platform} from 'react-native';
import type {DiscoverParams, PrinterInfo, PrintParams} from "./types";

const LINKING_ERROR =
  `The package 'react-native-epson-printer' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ios: "- You have run 'pod install'\n", default: ''}) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo managed workflow\n';

const EpsonPrinter = NativeModules.EpsonPrinter
  ? NativeModules.EpsonPrinter
  : new Proxy(
    {},
    {
      get() {
        throw new Error(LINKING_ERROR);
      },
    }
  );

export function discover(params: DiscoverParams): Promise<PrinterInfo[]> {
  return EpsonPrinter.discover(params);
}

export function print(params: PrintParams): Promise<String> {
  return EpsonPrinter.print(params);
}
