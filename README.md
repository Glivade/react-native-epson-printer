# react-native-epson-printer

A react native module for epson printer sdk.

## Installation

```sh
npm install react-native-epson-printer
```

## Usage

```js
import {discover, print} from "react-native-epson-printer";

// for discovery
const printers = await discover({interface_type: InterfaceType.LAN});

// for printing
const response = await print({
  printer: {name: 'Epson', interface_type: 'LAN', mac_address: '12:12:12:12:12:12', target: '192.168.0.100'},
  data: 'Test Print',
  receipt_copy_count: 1,
  font_size: FontSize.Small, // Small, Regular, Medium, Large
})
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

MIT
