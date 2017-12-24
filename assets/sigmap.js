var map = null;
var marker = null;
var towerMarker = null;
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
                            {minZoom: 12, maxZoom: 18, subdomains: "abc", attribution: 'Courtesy USGS.'});

var osm = L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                        {maxZoom: 18, attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>.'})

var shields = L.tileLayer('http://{s}.tile.openstreetmap.us/osmus_shields/{z}/{x}/{y}.png',
                           {maxZoom: 18, subdomains: "abc", attribution: '&copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>.'});

var sensorlySprint = L.tileLayer('http://tiles-day.cdn.sensorly.net/tile/any/lte_310sprint/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyTMobileUS = L.tileLayer('http://tiles-day.cdn.sensorly.net/tile/any/lte_310260/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyATT = L.tileLayer('http://tiles-day.cdn.sensorly.net/tile/any/lte_310410/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyVerizon = L.tileLayer('http://tiles-day.cdn.sensorly.net/tile/any/lte_310verizon/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyCSpire = L.tileLayer('http://tiles-day.cdn.sensorly.net/tile/any/lte_311230/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var sensorlyUSCellular = L.tileLayer('http://tiles-day.cdn.sensorly.net/tile/any/lte_310uscellular/{z}/{x}/{x}/{y}/{y}.png?s=256',
                           {maxZoom: 18, detectRetina: true, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});


var baseLayers = {'US Shields' : shields,
                  'Mapquest Open' : mapquest,
                  'Mapquest Aerial' : mqaerial,
                  'USGS Topos' : usgsTopos,
                  'USGS/NAIP Aerial' : usgsAerial,
                  'OpenStreetMap' : osm};

var baseLayerNames = {
    'osm' : osm,
    'shields' : shields,
    'mapquest' : mapquest,
    'topos' : usgsTopos,
    'usgs-aerial' : usgsAerial
}

var overlays = {'Sprint LTE' : sensorlySprint,
                'T-Mobile US LTE' : sensorlyTMobileUS,
                'Verizon LTE' : sensorlyVerizon,
                'AT&T LTE' : sensorlyATT,
                };

var currentBaseLayer = null;
var currentOverlayLayer = null;

function setBaseLayer(base) {
    var baseLayer = baseLayerNames[base]

    if(baseLayer != currentBaseLayer) {
        baseLayer.addTo(map)
        if(currentBaseLayer)
            map.removeLayer(currentBaseLayer)
        currentBaseLayer = baseLayer
        currentBaseLayer.bringToBack()
    }
}

function setOverlayLayer(operator) {
    var overlayLayer = null;

    if(operator == 'none')
        overlayLayer = null
    else if(operator == '310260')
        overlayLayer = sensorlyTMobileUS
    else if(operator == '310410' || operator == '310150')
        overlayLayer = sensorlyATT
    else if(operator == '310010' || operator == '311480')
        overlayLayer = sensorlyVerizon
    else if(operator == '311580')
        overlayLayer = sensorlyUSCellular
    else if(operator == '311230')
        overlayLayer = sensorlyCSpire
    else // Sprint 1900 (Band 25) is 310120; 2500 (Band 41) is 311490; ESMR (Band 26) is 316010?
        overlayLayer = sensorlySprint

    if(currentOverlayLayer != overlayLayer) {
        if(overlayLayer)
            overlayLayer.addTo(map);
        if(currentOverlayLayer)
            map.removeLayer(currentOverlayLayer);
        currentOverlayLayer = overlayLayer;
    }
}

function startmap(lat, lon, newzoom, base, operator) {
    if(newzoom)
        zoom = newzoom;

    map = L.map('map', {center: [lat, lon],
                        // fadeAnimation: false,
                        // zoomAnimation: false,
                        // markerZoomAnimation: false,
                        zoom: zoom
                       });

    setBaseLayer(base);
    setOverlayLayer(operator);

    L.control.scale().addTo(map);
//    L.control.layers(baseLayers, overlays).addTo(map);
}

var padding = 0.1;

function zoom4speed(speed) {
    speed = speed*3.6; // Convert to km/h from m/s

    if(speed >= 83)
        return 13
    else if (speed >= 63)
        return 14
    else if (speed >= 43)
        return 15
    else if (speed >= 23)
        return 16
    else if (speed >= 5)
        return 17

    return undefined; // Don't zoom
}

var arrowhead = L.icon({iconUrl: "images/Arrow_Blue_Up_001.svg", iconSize: [20, 20], iconAnchor: [10, 10]});

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

function recenter(lat, lon, radius, speed, bearing, stale, operator, base, towerRadius) {
    newZoom = zoom4speed(speed);

    if(!map) {
        startmap(lat, lon, newZoom, base, operator);
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

    if(isNaN(towerRadius))
        towerRadius = 0; /* Effectively hide the marker */

    if(!towerMarker) {
        towerMarker = L.circle(pos, towerRadius);
        towerMarker.setStyle({color: "#cc0", fillColor: "#cc0"});
        towerMarker.addTo(map);
    } else {
        towerMarker.setLatLng(pos);

        towerMarker.setRadius(towerRadius);
    }

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
        bounds = L.latLngBounds([pos, pmarker.getLatLng()])
        if(towerRadius > 0)
            bounds = bounds.extend(towerMarker.getBounds())
        bounds = bounds.pad(padding);
        map.fitBounds(bounds);
        lastZoom = 0;
    } else {
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

    bounds = L.latLngBounds([marker.getLatLng(), pmarker.getLatLng()]).pad(padding);
    map.fitBounds(bounds);
}

function clearMarker() {
    if(!map || !pmarker) return;

    map.removeLayer(pmarker);
    pmarker = null;
    lastZoom = Date.now();
}
