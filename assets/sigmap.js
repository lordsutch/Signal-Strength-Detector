var map = null;
var marker = null;
var zoom = 12;
var oldspeed = 0;
var pmarker = null;
var arrow = null;
var lastZoom = 0;

var mapquest = L.tileLayer('http://otile{s}.mqcdn.com/tiles/1.0.0/map/{z}/{x}/{y}.jpg',
                           {maxZoom: 18, subdomains: "1234", attribution: 'Map data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>. Tiles Courtesy of <a href="http://www.mapquest.com/" target="_blank">MapQuest</a> <img src="http://developer.mapquest.com/content/osm/mq_logo.png">'});

var mqaerial = L.tileLayer('http://otile{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.jpg',
                           {maxZoom: 18, subdomains: "1234", detectRetina: true, attribution: 'Portions Courtesy <a href="http://www.nasa.gov/">NASA</a>/<a href="http://www.jpl.nasa.gov/">JPL-Caltech</a> and <a href="http://www.fsa.usda.gov/">USDA Farm Service Agency</a>. Tiles Courtesy of <a href="http://www.mapquest.com/" target="_blank">MapQuest</a> <img src="http://developer.mapquest.com/content/osm/mq_logo.png">'});

var usgsAerial = L.tileLayer('http://{s}.tile.openstreetmap.us/usgs_large_scale/{z}/{x}/{y}.jpg',
                             {maxZoom: 18, subdomains: "abc", detectRetina: true, attribution: 'Courtesy USGS/NAIP.'});

var usgsTopos = L.tileLayer('http://{s}.tile.openstreetmap.us/usgs_scanned_topos/{z}/{x}/{y}.png',
                            {minZoom: 12, maxZoom: 18, subdomains: "abc", detectRetina: true, attribution: 'Courtesy USGS.'});

var shields = L.tileLayer('http://{s}.tile.openstreetmap.us/osmus_shields/{z}/{x}/{y}.png',
                           {maxZoom: 17, subdomains: "abc", attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>.'});

var sensorlySprint = L.tileLayer('http://tiles3.api.sensorly.com/tile/web/lte_310sprint/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyTMobileUS = L.tileLayer('http://tiles3.api.sensorly.com/tile/web/lte_310260/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyATT = L.tileLayer('http://tiles3.api.sensorly.com/tile/web/lte_310410/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyVerizon = L.tileLayer('http://tiles3.api.sensorly.com/tile/web/lte_310verizon/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});


var baseLayers = {'US Shields' : shields,
                  'Mapquest Open' : mapquest,
                  'Mapquest Aerial' : mqaerial,
                  'USGS Topos' : usgsTopos,
                  'USGS/NAIP Aerial' : usgsAerial};
var overlays = {'Sprint LTE' : sensorlySprint,
                'T-Mobile US LTE' : sensorlyTMobileUS,
                'Verizon LTE' : sensorlyVerizon,
                'AT&T LTE' : sensorlyATT,
                };

function startmap(lat, lon, newzoom, operator) {
    zoom = newzoom;
    map = L.map('map', {center: [lat, lon],
                        // fadeAnimation: false,
                        // zoomAnimation: false,
                        // markerZoomAnimation: false,
                        zoom: zoom
                       });

    shields.addTo(map);
    if(operator == '310260')
        sensorlyTMobileUS.addTo(map);
    else if(operator == '310410')
        sensorlyATT.addTo(map);
    else if(operator == '310010')
        sensorlyVerizon.addTo(map);
    else // Sprint 1900 (Band 25) is 310120; 2500 (Band 41) is 311490; ESMR (Band 26) is 316010
        sensorlySprint.addTo(map);

    L.control.scale().addTo(map);
    L.control.layers(baseLayers, overlays).addTo(map);
}

var padding = 0.1;

function zoom4speed(speed) {
    kmh = speed*3.6;

    if (kmh <= 20) {
        return 17;
    } else if (kmh <= 35) {
        return 16;
    } else if (kmh <= 65) {
        return 15;
    } else if (kmh <= 95) {
        return 14;
    } else {
        return 13;
    }
}

var arrowhead = L.icon({iconUrl: "images/Arrow_Blue_Up_001.svg",
                    iconSize: [20, 20], iconAnchor: [10, 10]});


// based on http://stackoverflow.com/questions/2187657/calculate-second-point-knowing-the-starting-point-and-distance
function zoomLoc(lat, lon, speed, bearing) {
    var theta  = Math.PI*bearing/180;

    var R = speed*150; // location in 2.5 minutes, in meters

    var dx = R*Math.sin(theta);
    var dy = R*Math.cos(theta);

    var dlon = dx/(111320*Math.cos(Math.PI*lat/180));
    var dlat = dy/110540;

    return L.latLng([lat+dlat, lon+dlon]);
}

function recenter(lat, lon, radius, speed, bearing, stale, operator) {
    newZoom = zoom4speed(speed);

    // Random stuff
    //if(Math.random()*15 < 1) {
    //    newZoom += Math.round(Math.random()*2-1);
    //    lat += Math.random()*0.02-0.01;
    //    lon += Math.random()*0.02-0.01;
    //}

    if(!map) {
        startmap(lat, lon, newZoom, operator);
        lastZoom = Date.now();
    }

    pos = L.latLng([lat, lon]);

    radius = radius*1.96;
    if(!marker) {
        marker = L.circle(pos, radius);
        marker.addTo(map);
    }

    if(stale) {
        marker.setStyle({color: "#f00", fillColor: "#f00"});
    } else {
        marker.setStyle({color: "#03f", fillColor: "#03f"});
    }

    marker.setLatLng(pos);
    marker.setRadius(radius); // Convert to 95% confidence (1.96 sd) from 68% (1 sd)
    marker.redraw();

    if(!stale && bearing > 0) {
        if(arrow) {
            arrow.setLatLng(pos);
            arrow.setIconAngle(bearing);
            arrow.update();
        } else {
            arrow = L.marker(pos, {iconAngle: bearing, icon: arrowhead});
            arrow.addTo(map);
        }
    } else if(arrow) {
        arrow.setLatLng(pos);
        arrow.update();
    }

    if(pmarker) {
        bounds = L.latLngBounds([pos, // zoomLoc(lat, lon, speed, bearing),
                                 pmarker.getLatLng()]).pad(padding);
        map.fitBounds(bounds);
        lastZoom = 0;
    } else {
        // bounds = L.latLngBounds([pos, zoomLoc(lat, lon, speed, bearing)]).pad(padding);
        // map.fitBounds(bounds);
        if((Date.now() - lastZoom) >= 5000) { // 5 sec
            map.setView(pos, newZoom);
            lastZoom = Date.now();
        } else {
            map.panTo(pos);
        }
    }
}

function placeMarker(lat, lon) {
    if(!map) return;

    if(!pmarker) {
        pmarker = L.marker([lat, lon], {title: "1X Base Station"});
        pmarker.addTo(map);
    } else {
        pmarker.setLatLng([lat, lon]);
        pmarker.update();
    }

    bounds = L.latLngBounds([marker.getLatLng(),
                             pmarker.getLatLng()]).pad(padding);
    map.fitBounds(bounds);
}

function clearMarker() {
    if(!map || !pmarker) return;

    map.removeLayer(pmarker);
    pmarker = null;
    lastZoom = Date.now();
}
