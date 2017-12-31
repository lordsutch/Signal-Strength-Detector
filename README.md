Signal Detector - LTE and ESMR base station tracking for Sprint phones
=============

Copyright (C) 2013 Chris Lawrence

Based on Signal Strength Detector
Copyright (C) 2011 Thomas James Barrasso

Author: Chris Lawrence
Name: Signal Detector 
Version: 2
License: Apache License, Version 2.0  

Description:
-------

This application tracks the base station IDs and signal strengths for
LTE (Sprint 1900 MHz "G" block, and possibly other bands on other
carriers) and 1xRTT base stations (on the ESMR band used by Sprint).
The data may be useful for mapping out base station locations and
coverage.

Developed for the S4GRU community at http://s4gru.com

This code may also work on non-Sprint LTE devices, but there are no
guarantees.  LTE base stations' IDs are only available currently with
HTC devices (only tested on HTC Evo 4G LTE) and Sprint's Galaxy Nexus
(with Android 4.2.1); they may also be available on devices running
custom ROMs based on Android 4.1 and later that implement the
CellIdentityLte API.  Other features should work with any CDMA Android
4.1+ phone.

Other Tools:
-----------

cellfinder - https://github.com/lordsutch/cellfinder

This Python code will take the cellinfolte.csv files produced by
SignalDetector and use them to try to geolocate base
stations.

Generally speaking it works pretty well (in my experience, it gets
"ground truth" about 90% of the time), but if you only have
observations passing a tower in a straight line at a substantial
distance, there may not be a single mathematical solution and it will
converge on the wrong one. For example, if you are on a straight road
that passes east of a tower (i.e. it goes past you to the west), the
tower may be geolocated east of the road rather than west of it.

License:
-------

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

Also includes Leaflet.js:

Copyright (c) 2010-2013, Vladimir Agafonkin
Copyright (c) 2010-2011, CloudMade
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

Also includes Marker.Rotate.js:

Copyright (c) 2011-2012, Pavel Shramov
All rights reserved. 

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

   1. Redistributions of source code must retain the above copyright
   notice, this list of conditions and the following disclaimer.

   2. Redistributions in binary form must reproduce the above
   copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided
   with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
