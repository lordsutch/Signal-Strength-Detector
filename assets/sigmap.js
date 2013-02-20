var map = null;
var marker = null;
var zoom = 12;
var oldspeed = 0;
var pmarker = null;
var arrow = null;

var sensorly = L.tileLayer('http://tiles3.api.sensorly.com/tile/web/lte_310sprint/{z}/{x}/{x}/{y}/{y}.png?s=512',
                           {maxZoom: 18, tileSize: 512, attribution: '&copy; <a href="http://www.sensorly.com/">Sensorly</a>.'});

var mapquest = L.tileLayer('http://otile{s}.mqcdn.com/tiles/1.0.0/map/{z}/{x}/{y}.jpg',
                           {maxZoom: 18, subdomains: "1234", attribution: 'Map data &copy; <a href="http://www.openstreetmap.org/copyright">OpenStreetMap</a>. Tiles Courtesy of <a href="http://www.mapquest.com/" target="_blank">MapQuest</a> <img src="http://developer.mapquest.com/content/osm/mq_logo.png">'});

var mqaerial = L.tileLayer('http://otile{s}.mqcdn.com/tiles/1.0.0/sat/{z}/{x}/{y}.jpg',
                           {maxZoom: 18, subdomains: "1234", attribution: 'Portions Courtesy <a href="http://www.nasa.gov/">NASA</a>/<a href="http://www.jpl.nasa.gov/">JPL-Caltech</a> and <a href="http://www.fsa.usda.gov/">USDA Farm Service Agency</a>. Tiles Courtesy of <a href="http://www.mapquest.com/" target="_blank">MapQuest</a> <img src="http://developer.mapquest.com/content/osm/mq_logo.png">'});

var baseLayers = {'Mapquest Open' : mapquest,
                  'Mapquest Aerial' : mqaerial};
var overlays = {'Sensorly' : sensorly};

function startmap(lat, lon, newzoom) {
    zoom = newzoom;
    map = L.map('map', {center: [lat, lon],
                        fadeAnimation: false,
                        zoomAnimation: false,
                        // markerZoomAnimation: false,
                        zoom: zoom
                       });

    mapquest.addTo(map);
    L.control.layers(baseLayers, overlays).addTo(map);
}

var padding = 0.15;

function zoom4speed(speed) {
    kmh = speed*3.6;

    if (kmh <= 25) {
        return 17;
    } else if (kmh <= 45) {
        return 16;
    } else if (kmh <= 90) {
        return 15;
    } else {
        return 14;
    }
}

arrowhead = L.icon({iconUrl: "images/Arrow_Blue_Up_001.svg",
                    iconSize: [30, 30], iconAnchor: [15, 15]});

function recenter(lat, lon, radius, speed, bearing) {
    newZoom = zoom4speed(speed);

    if(!map) startmap(lat, lon, newZoom);

    if(marker) {
        marker.setLatLng([lat, lon]);
        marker.setRadius(radius);
    } else {
        marker = L.circle([lat, lon], radius);
        marker.addTo(map);
    }

    if(bearing > 0) {
        if(arrow) {
            arrow.setLatLng([lat, lon]);
            arrow.setIconAngle(bearing);
        } else {
            arrow = L.marker([lat, lon], {iconAngle: bearing,
                                          icon: arrowhead});
            arrow.addTo(map);
        }
    } else if(arrow) {
        arrow.setLatLng([lat, lon]);
    }
    
    if(pmarker) {
        bounds = L.latLngBounds([[lat, lon],
                                 pmarker.getLatLng()]).pad(padding);
        map.fitBounds(bounds);
    } else {
        if(zoom != newZoom && speed != oldspeed) {
            map.setZoom(newZoom);
            zoom = newZoom;
            oldspeed = speed;
        }

        map.panTo([lat, lon]);
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
}
