

function renderNav() {
    const navRoot = document.getElementById('topnav-root');
    if (navRoot && window.TopNav && window.navItems) {
        ReactDOM.createRoot(navRoot).render(
            React.createElement(TopNav, { items: window.navItems })
        );
    }
}

let _taxGridRoot = null;

function collectAllSpecies(node, filterFn) {
    let result = filterFn ? (node.species || []).filter(filterFn) : (node.species || []).slice();
    (node.children || []).forEach(function(c) {
        result = result.concat(collectAllSpecies(c, filterFn));
    });
    return result;
}

function filterTaxonomyTree(node, filterFn) {
    const filteredSpecies = (node.species || []).filter(filterFn);
    const filteredChildren = (node.children || [])
        .map(function(child) { return filterTaxonomyTree(child, filterFn); })
        .filter(function(child) { return child !== null; });
    if (filteredSpecies.length === 0 && filteredChildren.length === 0) return null;
    return {
        name: node.name,
        rank: node.rank,
        category: node.category || null,
        species: filteredSpecies,
        children: filteredChildren
    };
}

function countSpeciesInNode(node) {
    let count = node.species ? node.species.length : 0;
    (node.children || []).forEach(function(c) { count += countSpeciesInNode(c); });
    return count;
}

function renderTaxonomyGrid(sections) {
    const el = document.getElementById('taxonomy-grid-root');
    if (!el || !window.TaxonomyGrid) return;
    if (!_taxGridRoot) _taxGridRoot = ReactDOM.createRoot(el);
    _taxGridRoot.render(React.createElement(TaxonomyGrid, { sections: sections }));
}

(function() {
    const e = React.createElement;

    function SidebarWrapper(props) {
        const treeMenuData = props.treeMenuData;
        const taxonomyOnly = props.taxonomyOnly || false;

        const modeState = React.useState(taxonomyOnly ? 'taxonomy' : 'categories');
        const viewMode = modeState[0];
        const setViewMode = modeState[1];

        const taxDataState = React.useState(null);
        const taxonomyData = taxDataState[0];
        const setTaxonomyData = taxDataState[1];

        const selectedState = React.useState(null);
        const selectedName = selectedState[0];
        const setSelectedName = selectedState[1];

        const expandPathState = React.useState(null);
        const expandPath = expandPathState[0];
        const setExpandPath = expandPathState[1];

        const pendingSelectState = React.useState(null);
        const pendingSelect = pendingSelectState[0];
        const setPendingSelect = pendingSelectState[1];

        const searchState = React.useState('');
        const searchQuery = searchState[0];
        const setSearchQuery = searchState[1];

        const superCatState = React.useState('ALL');
        const superCatSel = superCatState[0];
        const setSuperCatSel = superCatState[1];

        const selectedNodeRef = React.useRef(null);
        const gridTriggerState = React.useState(0);
        const gridTrigger = gridTriggerState[0];
        const setGridTrigger = gridTriggerState[1];

        // Expand/shrink the left column to fit tree content in taxonomy mode
        React.useEffect(function() {
            const leftCol = document.getElementById('leftcolumn');
            const contentCol = document.getElementById('contentcolumn');
            if (!leftCol || !contentCol) return;

            if (viewMode !== 'taxonomy') {
                leftCol.style.width = '';
                leftCol.style.maxWidth = '';
                contentCol.style.marginLeft = '';
                if (typeof panelOffset !== 'undefined') panelOffset = 200;
                return;
            }

            leftCol.style.width = 'max-content';
            leftCol.style.maxWidth = '45vw';

            function syncWidth() {
                const w = leftCol.offsetWidth;
                contentCol.style.marginLeft = w + 'px';
                if (typeof panelOffset !== 'undefined') panelOffset = w;
                // Let TaxonomyGrid re-calculate columns
                window.dispatchEvent(new Event('resize'));
            }

            const ro = new ResizeObserver(syncWidth);
            ro.observe(leftCol);
            syncWidth();

            return function() { ro.disconnect(); };
        }, [viewMode]);

        function searchNodes(data, query) {
            const results = [];
            const q = query.toLowerCase();
            function traverse(node) {
                const nameMatch = node.name && node.name.toLowerCase().indexOf(q) !== -1;
                const catMatch = node.category && node.category.toLowerCase().indexOf(q) !== -1;
                if (nameMatch || catMatch) results.push({ node: node });
                const spFilter = getSuperCatFilter();
                (node.species || []).forEach(function(sp) {
                    if (spFilter && !spFilter(sp)) return;
                    const spNameMatch = sp.name && sp.name.toLowerCase().indexOf(q) !== -1;
                    const spSciMatch = sp.sname && sp.sname.toLowerCase().indexOf(q) !== -1;
                    if (spNameMatch || spSciMatch) results.push({ node: node, species: sp });
                });
                (node.children || []).forEach(traverse);
            }
            const topNodes = data.children && data.children.length > 0 ? data.children : [data];
            topNodes.forEach(traverse);
            return results;
        }

        function getSuperCatFilter() {
            if (superCatSel === 'ALL') return null;
            return function(sp) {
                const sc = sp.superCat || '';
                if (superCatSel === 'FISH') return sc === 'Fish';
                if (superCatSel === 'INVERTEBRATES') return sc === 'Invertebrates' || sc === 'Sponges' || sc === 'Corals';
                if (superCatSel === 'OTHERS') return sc === 'Algae' || sc === 'Mammals' || sc === 'Other';
                return true;
            };
        }

        function buildSections(node, filterFn) {
            const result = drillDown(node);
            const effective = result.node;
            const path = result.path;
            let sections;
            if ((effective.children || []).length > 1) {
                const prefix = path.map(function(n) { return { name: n.name, rank: n.rank || null, category: n.category || null }; });
                sections = effective.children.map(function(child) {
                    return {
                        breadcrumb: prefix.concat([{ name: child.name, rank: child.rank || null, category: child.category || null }]),
                        species: collectAllSpecies(child, filterFn)
                    };
                });
            } else {
                sections = [{
                    breadcrumb: path.map(function(n) { return { name: n.name, rank: n.rank || null, category: n.category || null }; }),
                    species: collectAllSpecies(effective, filterFn)
                }];
            }
            // If no breadcrumb item has a category, find the nearest ancestor with one
            const anyCategory = sections.some(function(s) {
                return s.breadcrumb.some(function(c) { return c.category; });
            });
            if (!anyCategory && taxonomyData) {
                const rootPath = findPathFromRoot(taxonomyData, node.name);
                if (rootPath) {
                    for (let i = rootPath.length - 2; i >= 0; i--) {
                        if (rootPath[i].category) {
                            const ancestorCrumb = { name: rootPath[i].name, rank: rootPath[i].rank || null, category: rootPath[i].category };
                            sections = sections.map(function(s) {
                                return { breadcrumb: [ancestorCrumb].concat(s.breadcrumb), species: s.species };
                            });
                            break;
                        }
                    }
                }
            }
            return sections;
        }

        // Rebuild grid and re-expand tree when selected node or supercat filter changes
        React.useEffect(function() {
            if (!selectedNodeRef.current) return;
            const node = selectedNodeRef.current;
            if (taxonomyData) {
                const ancestorPath = findPathFromRoot(taxonomyData, node.name);
                if (ancestorPath) {
                    const pathSet = {};
                    ancestorPath.forEach(function(n) { pathSet[n.name] = true; });
                    setExpandPath(pathSet);
                }
            }
            renderTaxonomyGrid(buildSections(node, getSuperCatFilter()));
        }, [gridTrigger, superCatSel]);

        function switchMode(mode) {
            setSearchQuery('');
            setViewMode(mode);
            const topTable = document.getElementById('TopTable');
            const taxGrid = document.getElementById('taxonomy-grid-root');
            if (mode === 'taxonomy') {
                if (topTable) topTable.style.display = 'none';
                if (taxGrid) taxGrid.style.display = 'block';
                if (!taxonomyData && typeof reefRef !== 'undefined') {
                    fetch('/taxonomy_region_' + reefRef + '.json')
                        .then(function(r) { return r.json(); })
                        .then(function(data) { setTaxonomyData(data); });
                }
                if (!parseTaxonHash() && typeof lcaName !== 'undefined' && lcaName) {
                    setPendingSelect(lcaName);
                }
                renderTaxonomyGrid([]);
            } else {
                if (topTable) topTable.style.display = '';
                if (taxGrid) taxGrid.style.display = 'none';
                setSelectedName(null);
            }
        }

        function drillDown(node) {
            const path = [];
            let current = node;
            while (true) {
                path.push(current);
                const children = current.children || [];
                if (children.length === 1 && !(current.species && current.species.length > 0)) {
                    current = children[0];
                } else {
                    break;
                }
            }
            return { node: current, path: path };
        }

        function findPathFromRoot(root, targetName) {
            if (root.name === targetName) return [root];
            const children = root.children || [];
            for (let i = 0; i < children.length; i++) {
                const childPath = findPathFromRoot(children[i], targetName);
                if (childPath) return [root].concat(childPath);
            }
            return null;
        }

        function parseTaxonHash() {
            const hash = window.location.hash;
            if (!hash) return null;
            const prefix = '#taxon=';
            if (hash.indexOf(prefix) !== 0) return null;
            return decodeURIComponent(hash.slice(prefix.length));
        }

        function selectFromHash(data) {
            const name = parseTaxonHash();
            if (!name) return;
            const path = findPathFromRoot(data, name);
            if (!path) return;
            handleNodeSelect(path[path.length - 1]);
        }

        // Auto-switch to taxonomy mode for #taxon= hashes, or always if taxonomyOnly
        React.useEffect(function() {
            if (taxonomyOnly || parseTaxonHash()) {
                switchMode('taxonomy');
            }
        }, []);

        // Auto-select node once taxonomy data is ready
        React.useEffect(function() {
            if (viewMode !== 'taxonomy' || !taxonomyData) return;
            selectFromHash(taxonomyData);
        }, [viewMode, taxonomyData]);

        // Handle back/forward navigation and direct hash links
        React.useEffect(function() {
            function onHashChange() {
                if (!parseTaxonHash()) return;
                if (viewMode !== 'taxonomy') {
                    switchMode('taxonomy');
                    // selectFromHash will fire via the [viewMode, taxonomyData] effect once data loads
                } else if (taxonomyData) {
                    selectFromHash(taxonomyData);
                }
            }
            window.addEventListener('hashchange', onHashChange);
            return function() { window.removeEventListener('hashchange', onHashChange); };
        }, [viewMode, taxonomyData]);

        // Auto-select lcaName when Taxonomy button clicked
        React.useEffect(function() {
            if (!pendingSelect || !taxonomyData) return;
            const path = findPathFromRoot(taxonomyData, pendingSelect);
            if (path) handleNodeSelect(path[path.length - 1]);
            setPendingSelect(null);
        }, [pendingSelect, taxonomyData]);

        function handleNodeSelect(node) {
            window.scrollTo(0, 0);
            history.replaceState(null, '', '#taxon=' + encodeURIComponent(node.name));
            setSelectedName(node.name);
            if (taxonomyData) {
                const ancestorPath = findPathFromRoot(taxonomyData, node.name);
                if (ancestorPath) {
                    const pathSet = {};
                    ancestorPath.forEach(function(n) { pathSet[n.name] = true; });
                    setExpandPath(pathSet);
                }
            }
            selectedNodeRef.current = node;
            setGridTrigger(function(k) { return k + 1; });
        }

        const filterFn = getSuperCatFilter();
        const filteredTaxonomyData = filterFn && taxonomyData
            ? filterTaxonomyTree(taxonomyData, filterFn)
            : taxonomyData;

        const searchResults = searchQuery && filteredTaxonomyData ? searchNodes(filteredTaxonomyData, searchQuery) : null;

        return e(React.Fragment, null,
            taxonomyOnly ? null : e('div', { className: 'view-toggle' },
                e('label', null,
                    e('input', {
                        type: 'radio',
                        name: 'sidebar-view',
                        value: 'categories',
                        checked: viewMode === 'categories',
                        onChange: function() { switchMode('categories'); }
                    }),
                    'Categories'
                ),
                e('label', null,
                    e('input', {
                        type: 'radio',
                        name: 'sidebar-view',
                        value: 'taxonomy',
                        checked: viewMode === 'taxonomy',
                        onChange: function() { switchMode('taxonomy'); }
                    }),
                    'Taxonomy'
                )
            ),
            viewMode === 'taxonomy' ? e('div', { className: 'supercat-filter' },
                [
                    { value: 'ALL', label: 'All' },
                    { value: 'FISH', label: 'Fish' },
                    { value: 'INVERTEBRATES', label: 'Invertebrates' },
                    { value: 'OTHERS', label: 'Others' }
                ].map(function(opt) {
                    return e('label', { key: opt.value },
                        e('input', {
                            type: 'radio',
                            name: 'supercat',
                            value: opt.value,
                            checked: superCatSel === opt.value,
                            onChange: function() { setSuperCatSel(opt.value); }
                        }),
                        opt.label
                    );
                })
            ) : null,
            viewMode === 'taxonomy' ? e('div', { className: 'taxon-search' },
                e('input', {
                    type: 'text',
                    placeholder: 'Search...\u2026',
                    value: searchQuery,
                    onChange: function(ev) { setSearchQuery(ev.target.value); }
                })
            ) : null,
            viewMode === 'categories'
                ? e(AccordionMenu, { data: treeMenuData })
                : searchResults
                    ? (searchResults.length === 0
                        ? e('div', { className: 'taxon-search-empty' }, 'No matches found.')
                        : e('div', { className: 'taxon-search-results' },
                            searchResults.slice(0, 100).map(function(r, i) {
                                return e('div', {
                                    key: i,
                                    className: 'taxon-search-result',
                                    onClick: function() {
                                        setSearchQuery('');
                                        handleNodeSelect(r.node);
                                    }
                                }, r.species
                                    ? e(React.Fragment, null,
                                        e('span', { className: 'taxon-name' }, r.species.name),
                                        e('span', { className: 'taxon-category' }, '\u00a0'),
                                        e('span', { className: 'taxon-rank' }, r.species.sname)
                                    )
                                    : e(React.Fragment, null,
                                        e('span', { className: 'taxon-rank' }, (r.node.rank || '') + '\u00a0'),
                                        e('span', { className: 'taxon-name' }, r.node.name),
                                        r.node.category ? e('span', { className: 'taxon-category' }, '\u00a0\u2013\u00a0' + r.node.category) : null,
                                        e('span', { className: 'taxon-count' }, '\u00a0(' + countSpeciesInNode(r.node) + ')')
                                    )
                                );
                            })
                          )
                    )
                    : e(TaxonomyTree, {
                        data: filteredTaxonomyData,
                        selectedName: selectedName,
                        onSelect: handleNodeSelect,
                        expandPath: expandPath
                    })
        );
    }

    window.SidebarWrapper = SidebarWrapper;
})();

function renderAccordion() {
    const accRoot = document.getElementById('accordion-root');
    if (accRoot && window.AccordionMenu && window.treeMenuData) {
        ReactDOM.createRoot(accRoot).render(
            React.createElement(SidebarWrapper, { treeMenuData: window.treeMenuData, taxonomyOnly: !!window.taxonomyOnly })
        );
    }
}

function mainInit() {
    renderNav();
    renderAccordion();

    creategrid();

    const hash = location.hash;
    if (hash !== "") {
        goToByScroll(hash);
    }

    window.addEventListener('resize', creategrid);

    $scrollingDiv = document.getElementById('accordion-root');
    if ($scrollingDiv) {
        $divpos = Math.round($scrollingDiv.getBoundingClientRect().top + window.pageYOffset - 15);
        $margin = 0;

        window.addEventListener('scroll', function() {
            doScroll();
        });
    }
}


function doScroll() {

    topz = window.pageYOffset;
    heightz = window.innerHeight;
    bottomz = topz + heightz;
    heightDiv = $scrollingDiv.offsetHeight + 50;

    if(topz > $divpos) {
        if(heightDiv < heightz) {
          animateMarginTop($scrollingDiv, (topz - $divpos), 500);
          $margin = (topz - $divpos);
        }
        else {
            topv = (($divpos - topz) + $margin);
            bottomv = (topv + heightDiv) - heightz;
            if((topv < 0) && (bottomv > 0)) {
                    // Nothing
            }
            else if(topv > 0) {
                animateMarginTop($scrollingDiv, (topz - $divpos), 500);
                $margin = (topz - $divpos);
            }
            else {
                animateMarginTop($scrollingDiv, ((topz + heightz) - (heightDiv + $divpos)), 500);
                $margin = (topz + heightz) - (heightDiv + $divpos);
            }
        }
    }
    else {
        cancelAnimateMarginTop($scrollingDiv);
        $scrollingDiv.style.marginTop = "0px";
        $margin = 0;
    }
}

let _scrollAnimId = null;
function animateMarginTop(el, target, duration) {
    if (_scrollAnimId) cancelAnimationFrame(_scrollAnimId);
    const start = parseFloat(el.style.marginTop) || 0;
    let startTime = null;
    function step(timestamp) {
        if (!startTime) startTime = timestamp;
        const progress = Math.min((timestamp - startTime) / duration, 1);
        el.style.marginTop = (start + (target - start) * progress) + "px";
        if (progress < 1) {
            _scrollAnimId = requestAnimationFrame(step);
        } else {
            _scrollAnimId = null;
        }
    }
    _scrollAnimId = requestAnimationFrame(step);
}

function cancelAnimateMarginTop(el) {
    if (_scrollAnimId) {
        cancelAnimationFrame(_scrollAnimId);
        _scrollAnimId = null;
    }
}

function FamInit() {
    renderNav();
    renderAccordion();
    SpeciesInit();

    $scrollingDiv = document.getElementById('accordion-root');
    if ($scrollingDiv) {
        $divpos = Math.round($scrollingDiv.getBoundingClientRect().top + window.pageYOffset - 15);
        $margin = 0;

        window.addEventListener('scroll', function() {
            doScroll();
        });
    }
}

function renderSearch() {
    const searchRoot = document.getElementById('search-root');
    if (searchRoot && window.SpeciesSearch) {
        ReactDOM.createRoot(searchRoot).render(
            React.createElement(SpeciesSearch)
        );
    }
}

function SpeciesInit() {
    renderNav();
    renderSearch();
}

function photoInit() {
    renderNav();
}

function resize() {
    const leftCol = document.getElementById('leftcolumn');
    const topSec = document.getElementById('topsection');
    if (leftCol && topSec) {
        leftCol.style.height = (document.documentElement.scrollHeight - topSec.offsetHeight) + 'px';
    }
}

function goToByScroll(id) {
    let el;
    try { el = document.querySelector(id); } catch(e) { return; }
    if (!el) return;

    const top = el.getBoundingClientRect().top + window.pageYOffset;
    if (top > 300) {
        window.scrollTo({ top: top - 10, behavior: 'smooth' });
    }

    const sel = id.replace(/#/g, "").replace(/_/g, " ");
    const headers = document.querySelectorAll('.catheader');
    headers.forEach(function(header) {
        const text = (header.textContent || header.innerText || "").trim();
        if (text === sel) {
            header.style.borderColor = "red";
            const link = header.querySelector('a');
            if (link) link.style.color = "red";
            setTimeout(function() {
                header.style.borderColor = "#dcd637";
                if (link) link.style.color = "#dcd637";
            }, 2000);
        }
    });
}
