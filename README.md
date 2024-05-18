## MGD H5 WebLibrary SDK : A Lightweight And High-performance Hybrid Framework
[![license](http://img.shields.io/badge/license-BSD3-brightgreen.svg?style=flat)](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/pulls)
---

 MGD H5 WebLibrary SDK is a lightweight and high-performance Hybrid framework developed originaly by Tencent team,  which is intended to speed up the first screen of websites working on Android and iOS platform.
 Not only does MGD H5 WebLibrary SDK supports the static or dynamic websites which are rendered by server, but it is also compatible with web offline resource perfectly. 

 MGD H5 WebLibrary SDK uses custom url connection instead of original network connection to request the index html, so it can request resource in advance or parallel to avoid waiting for the view initialization.
 In this parallel case, MGD H5 WebLibrary SDK can read and render partial data by WebKit or Blink kernel without spending too much time waiting for the end of data stream.

 MGD H5 WebLibrary SDK can cache html cleverly according to MGD Specification obeyed by client and server.
 MGD H5 WebLibrary SDK Specification specify template and data by inserting different comment anchor, templates are bigger parts of html which stay the same or changed rarely , in contradiction data, which is the smaller and constantly change part of html.
 According to this, MGD H5 WebLibrary SDK request less data by incremental updating templates and data, the websites are faster and feel more like native application.
 In conclusion, MGD H5 WebLibrary SDK effectively enhance the user experience and increase click rate, retention rate and other indicators.

 MGDWeb is called for short in project.

### Before and After Using MGD H5 Web SDK

Pic 1: Before Using MGDWeb |  Pic 2: After Using MGDWeb
:-------------------------:|:-------------------------:
![default mode][1]  |  ![MGDWeb mode][2]

## Getting started

[Getting started with Android](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/sample/README.md)

## Demo Downloads
1. [Here](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/releases) are the latest sample demo for Android.


## Support
Any problem?

1. Learn more from the following sample. </br>
[Android sample](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/tree/master/sample)  </br>

2. Read the following source code </br>
[Android source code](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/tree/master/sdk) </br>


## Contributing
For more information about contributing issues or pull requests, see our [MGD Contributing Guide](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/CONTRIBUTING.md).

## License
MGDWeb is under the BSD license. See the [LICENSE](https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/LICENSE) file for details.

## The End

MGD's mission is MAKING WEB MUCH BETTER!

[1]: https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/assets/mgdweb_before.gif
[2]: https://github.com/jbr-madgamingdev/MGDH5WebLibrary/blob/master/assets/mgdweb_after.gif

Thank you for reading ~
