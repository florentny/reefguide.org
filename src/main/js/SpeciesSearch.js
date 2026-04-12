'use strict';

(function() {
    const e = React.createElement;

    const REGIONS = [
        {id: 0, name: 'All', path: '', code: 'all'},
        {id: 1, name: 'Caribbean', path: 'carib/', code: 'caribbean'},
        {id: 2, name: 'Indo-Pacific', path: 'indopac/', code: 'indopac'},
        {id: 3, name: 'Florida Keys', path: 'keys/', code: 'florida'},
        {id: 4, name: 'Hawaii', path: 'hawaii/', code: 'hawaii'},
        {id: 5, name: 'Easter Pacific', path: 'baja/', code: 'baja'},
        {id: 6, name: 'French Polynesia', path: 'fp/', code: 'fp'}
    ];

    function getUrlValue(key) {
        const params = new URLSearchParams(window.location.search);
        return params.get(key);
    }

    function reefSearch(species, query) {
        const words = query.split(' ');
        const fields = ['fullname', 'sname', 'subcategory', 'category', 'synonyms', 'aka'];
        for (let f = 0; f < fields.length; f++) {
            let allMatch = true;
            for (let i = 0; i < words.length; i++) {
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
        const species = props.species;
        const selRegion = props.selRegion;
        const xquery = props.xquery;
        const even = props.even;

        const state = React.useState(false);
        const details = state[0];
        const setDetails = state[1];

        const thumbState = React.useState('');
        const thumb = thumbState[0];
        const setThumb = thumbState[1];

        function handleClick() {
            if (!details) {
                setThumb('pix/thumb/' + species.name + species.thumb1 + '.jpg');
            }
            setDetails(!details);
        }

        const rowClass = 'red' + (even ? ' even' : '');

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
        const areaParam = getUrlValue('area');
        let initialRegion = 0;
        if (areaParam !== null) {
            const parsed = parseInt(areaParam, 10);
            if (!isNaN(parsed) && parsed >= 0 && parsed <= 5) {
                initialRegion = parsed;
            }
        }

        const regionState = React.useState(REGIONS[initialRegion]);
        const selRegion = regionState[0];
        const setSelRegion = regionState[1];

        const queryState = React.useState('');
        const query = queryState[0];
        const setQuery = queryState[1];

        const listState = React.useState([]);
        const speciesList = listState[0];
        const setSpeciesList = listState[1];

        const loadingState = React.useState(true);
        const loading = loadingState[0];
        const setLoading = loadingState[1];

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
            const idx = parseInt(ev.target.value, 10);
            const region = REGIONS[idx];
            setSelRegion(region);
            fetchSpecies(region);
        }

        function handleQueryChange(ev) {
            setQuery(ev.target.value);
        }

        const showResults = query.length >= 3;
        const xquery = query.replace(/ /g, '+');

        const filtered = [];
        if (showResults) {
            for (let i = 0; i < speciesList.length && filtered.length < 2000; i++) {
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
