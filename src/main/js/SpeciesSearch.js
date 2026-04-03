'use strict';

(function() {
    var e = React.createElement;

    var REGIONS = [
        {id: 0, name: 'All', path: '', code: 'all'},
        {id: 1, name: 'Caribbean', path: 'carib/', code: 'caribbean'},
        {id: 2, name: 'Indo-Pacific', path: 'indopac/', code: 'indopac'},
        {id: 3, name: 'Florida Keys', path: 'keys/', code: 'florida'},
        {id: 4, name: 'Hawaii', path: 'hawaii/', code: 'hawaii'},
        {id: 5, name: 'Easter Pacific', path: 'baja/', code: 'baja'},
        {id: 6, name: 'French Polynesia', path: 'fp/', code: 'fp'}
    ];

    function getUrlValue(key) {
        var params = new URLSearchParams(window.location.search);
        return params.get(key);
    }

    function reefSearch(species, query) {
        var words = query.split(' ');
        var fields = ['fullname', 'sname', 'subcategory', 'category', 'synonyms', 'aka'];
        for (var f = 0; f < fields.length; f++) {
            var allMatch = true;
            for (var i = 0; i < words.length; i++) {
                if (species[fields[f]].toLowerCase().indexOf(words[i].toLowerCase()) < 0) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }

    function SpeciesRow(props) {
        var species = props.species;
        var selRegion = props.selRegion;
        var xquery = props.xquery;
        var even = props.even;

        var state = React.useState(false);
        var details = state[0];
        var setDetails = state[1];

        var thumbState = React.useState('');
        var thumb = thumbState[0];
        var setThumb = thumbState[1];

        function handleClick() {
            if (!details) {
                setThumb('pix/thumb/' + species.name + species.thumb1 + '.jpg');
            }
            setDetails(!details);
        }

        var rowClass = 'red' + (even ? ' even' : '');

        if (details) {
            return e('tr', { className: even ? 'even' : '', onClick: handleClick },
                e('td', { colSpan: 4 },
                    e('div', { style: { display: 'inline-block' } },
                        e('img', { className: 'thumb', style: { height: '80px' }, src: thumb })
                    ),
                    e('div', { className: 'searchdetails' },
                        e('span', { className: 'sdname' }, species.fullname),
                        e('span', { className: 'sdsname' }, ', ' + species.sname),
                        e('br'),
                        'Category: ' + species.subcategory,
                        e('br'),
                        'Order: ', e('span', { className: 'sdsname' }, species.order), '\u00a0\u00a0\u00a0\u00a0Family: ', e('span', { className: 'sdsname' }, species.family),
                        e('br'),
                        e('a', {
                            href: selRegion.path + species.name + '.html?search=' + xquery + '&area=' + selRegion.code,
                            target: '_self',
                            onClick: function(ev) { ev.stopPropagation(); }
                        }, 'Go to ' + species.fullname + ' page')
                    )
                )
            );
        }

        return e('tr', { className: rowClass, onClick: handleClick },
            e('td', null, '\u00a0\u00a0'),
            e('td', null, species.fullname),
            e('td', null, e('i', null, species.sname)),
            e('td', null, species.subcategory)
        );
    }

    function SpeciesSearch() {
        var areaParam = getUrlValue('area');
        var initialRegion = 0;
        if (areaParam !== null) {
            var parsed = parseInt(areaParam, 10);
            if (!isNaN(parsed) && parsed >= 0 && parsed <= 5) {
                initialRegion = parsed;
            }
        }

        var regionState = React.useState(REGIONS[initialRegion]);
        var selRegion = regionState[0];
        var setSelRegion = regionState[1];

        var queryState = React.useState('');
        var query = queryState[0];
        var setQuery = queryState[1];

        var listState = React.useState([]);
        var speciesList = listState[0];
        var setSpeciesList = listState[1];

        var loadingState = React.useState(true);
        var loading = loadingState[0];
        var setLoading = loadingState[1];

        function fetchSpecies(region) {
            setLoading(true);
            fetch('/species_region_' + region.id + '.json')
                .then(function(res) { return res.json(); })
                .then(function(data) {
                    setSpeciesList(data);
                    setLoading(false);
                });
        }

        React.useEffect(function() {
            fetchSpecies(selRegion);
        }, []);

        function handleRegionChange(ev) {
            var idx = parseInt(ev.target.value, 10);
            var region = REGIONS[idx];
            setSelRegion(region);
            fetchSpecies(region);
        }

        function handleQueryChange(ev) {
            setQuery(ev.target.value);
        }

        var showResults = query.length >= 3;
        var xquery = query.replace(/ /g, '+');

        var filtered = [];
        if (showResults) {
            for (var i = 0; i < speciesList.length && filtered.length < 2000; i++) {
                if (reefSearch(speciesList[i], query)) {
                    filtered.push(speciesList[i]);
                }
            }
        }

        return e('div', null,
            e('br'),
            e('div', { className: 'searchpanel' },
                e('br'),
                e('div', { style: { margin: 'auto', textAlign: 'center' } },
                    'Search:\u00a0',
                    e('input', { value: query, onChange: handleQueryChange }),
                    ' \u00a0\u00a0\u00a0Region:\u00a0',
                    e('select', { value: selRegion.id, onChange: handleRegionChange },
                        REGIONS.map(function(r) {
                            return e('option', { key: r.id, value: r.id }, r.name);
                        })
                    ),
                    '\u00a0',
                    e('img', {
                        src: loading ? 'images/loading.gif' : 'images/loading2.gif',
                        id: 'loading-indicator'
                    })
                ),
                e('br'),
                e('table', { className: 'result' },
                    e('thead', { className: 'result' },
                        e('tr', null,
                            e('th'),
                            e('th', null, 'Species Name'),
                            e('th', null, 'Scientific Name'),
                            e('th', null, 'Category')
                        )
                    ),
                    e('tbody', null,
                        filtered.map(function(species, idx) {
                            return e(SpeciesRow, {
                                key: species.name,
                                species: species,
                                selRegion: selRegion,
                                xquery: xquery,
                                even: idx % 2 === 1
                            });
                        })
                    )
                ),
                e('br'), e('br'), e('br')
            ),
            e('br'), e('br'), e('br')
        );
    }

    window.SpeciesSearch = SpeciesSearch;
})();
