import * as React from 'react';

import {FlatList, SafeAreaView, StyleSheet, Text, TouchableOpacity, View} from 'react-native';
import {discover, print, printImage} from 'react-native-epson-printer';
import type {PrinterInfo} from "../../src/types";
import {FontSize, InterfaceType} from "../../src/types";

export default function App() {
  const [message, setMessage] = React.useState<any>();
  const [printers, setPrinters] = React.useState<PrinterInfo[]>();

  const scanNetwork = async () => {
    setMessage("Searching for network printers..")
    try {
      const printers = await discover({interface_type: InterfaceType.LAN})
      if (printers.length === 0) setMessage("No network printers found")
      else setMessage(`${printers.length} network printer(s) found`)
      setPrinters(printers)
    } catch (error) {
      // @ts-ignore
      setMessage(error.toString())
    }
  }

  const scanBluetooth = async () => {
    setMessage("Searching for bluetooth printers..")
    try {
      const printers = await discover({interface_type: InterfaceType.Bluetooth})
      if (printers.length === 0) setMessage("No bluetooth printers found")
      else setMessage(`${printers.length} bluetooth printer(s) found`)
      setPrinters(printers)
    } catch (error) {
      // @ts-ignore
      setMessage(error.toString())
    }
  }

  const sendPrint = async (printer: PrinterInfo) => {
    try {
      const response = await print({
        printer,
        data: "Test Print",
        receipt_copy_count: 1,
        font_size: FontSize.Small
      })
      setMessage(response)
    } catch (error) {
      // @ts-ignore
      setMessage(error.toString())
    }
  }

  const sendPrintImage = async (printer: PrinterInfo) => {
    try {
      const response = await printImage({
        printer,
        receipt_copy_count: 1,
      })
      setMessage(response)
    } catch (error) {
      console.log(error)
      // @ts-ignore
      setMessage(error.toString())
    }
  }

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={scanNetwork}>
          <View style={styles.button}>
            <Text style={styles.buttonText}>Scan Network</Text>
          </View>
        </TouchableOpacity>
        <TouchableOpacity onPress={scanBluetooth}>
          <View style={styles.button}>
            <Text style={styles.buttonText}>Scan Bluetooth</Text>
          </View>
        </TouchableOpacity>
      </View>
      <Text style={styles.message}>Info: {message}</Text>
      <FlatList
        data={printers}
        renderItem={({item}) => {
          return (
            <TouchableOpacity key={item.target} onPress={() => sendPrintImage(item)}>
              <View style={styles.printer}>
                <Text style={styles.printerName}>{item.name}</Text>
                <Text style={styles.printerDesc}>{item.target}</Text>
              </View>
            </TouchableOpacity>
          )
        }}/>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: "row",
    justifyContent: 'space-evenly'
  },
  button: {
    width: '100%',
    margin: 20,
    alignItems: "center",
    backgroundColor: "#8e44ad",
    padding: 10
  },
  buttonText: {
    color: '#ffffff',
    fontSize: 16,
  },
  message: {
    padding: 12,
  },
  printer: {
    padding: 12,
  },
  printerName: {
    fontSize: 18
  },
  printerDesc: {
    fontSize: 14
  },
});
