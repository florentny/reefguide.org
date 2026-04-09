

function renderNav() {
    var navRoot = document.getElementById('topnav-root');
    if (navRoot && window.TopNav && window.navItems) {
        ReactDOM.createRoot(navRoot).render(
            React.createElement(TopNav, { items: window.navItems })
        );
    }
}

var _taxGridRoot = null;

function collectAllSpecies(node) {
    var result = (node.species || []).slice();
    (node.children || []).forEach(function(c) {
        result = result.concat(collectAllSpecies(c));
    });
    return result;
}

function renderTaxonomyGrid(species, title) {
    var el = document.getElementById('taxonomy-grid-root');
    if (!el || !window.TaxonomyGrid) return;
    if (!_taxGridRoot) _taxGridRoot = ReactDOM.createRoot(el);
    _taxGridRoot.render(React.createElement(TaxonomyGrid, { species: species, title: title }));
}

(function() {
    var e = React.createElement;

    function SidebarWrapper(props) {
        var treeMenuData = props.treeMenuData;

        var modeState = React.useState('categories');
        var viewMode = modeState[0];
        var setViewMode = modeState[1];

        var taxDataState = React.useState(null);
        var taxonomyData = taxDataState[0];
        var setTaxonomyData = taxDataState[1];

        var selectedState = React.useState(null);
        var selectedName = selectedState[0];
        var setSelectedName = selectedState[1];

        // Expand/shrink the left column to fit tree content in taxonomy mode
        React.useEffect(function() {
            var leftCol = document.getElementById('leftcolumn');
            var contentCol = document.getElementById('contentcolumn');
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
                var w = leftCol.offsetWidth;
                contentCol.style.marginLeft = w + 'px';
                if (typeof panelOffset !== 'undefined') panelOffset = w;
                // Let TaxonomyGrid re-calculate columns
                window.dispatchEvent(new Event('resize'));
            }

            var ro = new ResizeObserver(syncWidth);
            ro.observe(leftCol);
            syncWidth();

            return function() { ro.disconnect(); };
        }, [viewMode]);

        function switchMode(mode) {
            setViewMode(mode);
            var topTable = document.getElementById('TopTable');
            var taxGrid = document.getElementById('taxonomy-grid-root');
            if (mode === 'taxonomy') {
                if (topTable) topTable.style.display = 'none';
                if (taxGrid) taxGrid.style.display = 'block';
                if (!taxonomyData && typeof reefRef !== 'undefined') {
                    fetch('/taxonomy_region_' + reefRef + '.json')
                        .then(function(r) { return r.json(); })
                        .then(function(data) { setTaxonomyData(data); });
                }
                renderTaxonomyGrid([], null);
            } else {
                if (topTable) topTable.style.display = '';
                if (taxGrid) taxGrid.style.display = 'none';
                setSelectedName(null);
            }
        }

        function handleNodeSelect(node) {
            setSelectedName(node.name);
            var species = collectAllSpecies(node);
            var label = node.rank ? node.name + ' (' + node.rank + ')' : node.name;
            renderTaxonomyGrid(species, label);
        }

        return e(React.Fragment, null,
            e('div', { className: 'view-toggle' },
                e('button', {
                    className: 'toggle-btn' + (viewMode === 'categories' ? ' active' : ''),
                    onClick: function() { switchMode('categories'); }
                }, 'Categories'),
                e('button', {
                    className: 'toggle-btn' + (viewMode === 'taxonomy' ? ' active' : ''),
                    onClick: function() { switchMode('taxonomy'); }
                }, 'Taxonomy')
            ),
            viewMode === 'categories'
                ? e(AccordionMenu, { data: treeMenuData })
                : e(TaxonomyTree, {
                    data: taxonomyData,
                    selectedName: selectedName,
                    onSelect: handleNodeSelect
                })
        );
    }

    window.SidebarWrapper = SidebarWrapper;
})();

function renderAccordion() {
    var accRoot = document.getElementById('accordion-root');
    if (accRoot && window.AccordionMenu && window.treeMenuData) {
        ReactDOM.createRoot(accRoot).render(
            React.createElement(SidebarWrapper, { treeMenuData: window.treeMenuData })
        );
    }
}

function mainInit() {
    renderNav();
    renderAccordion();

    creategrid();

    var hash = location.hash;
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

var _scrollAnimId = null;
function animateMarginTop(el, target, duration) {
    if (_scrollAnimId) cancelAnimationFrame(_scrollAnimId);
    var start = parseFloat(el.style.marginTop) || 0;
    var startTime = null;
    function step(timestamp) {
        if (!startTime) startTime = timestamp;
        var progress = Math.min((timestamp - startTime) / duration, 1);
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
    var searchRoot = document.getElementById('search-root');
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
    var leftCol = document.getElementById('leftcolumn');
    var topSec = document.getElementById('topsection');
    if (leftCol && topSec) {
        leftCol.style.height = (document.documentElement.scrollHeight - topSec.offsetHeight) + 'px';
    }
}

function goToByScroll(id) {
    var el;
    try { el = document.querySelector(id); } catch(e) { return; }
    if (!el) return;

    var top = el.getBoundingClientRect().top + window.pageYOffset;
    if (top > 300) {
        window.scrollTo({ top: top - 10, behavior: 'smooth' });
    }

    var sel = id.replace(/#/g, "").replace(/_/g, " ");
    var headers = document.querySelectorAll('.catheader');
    headers.forEach(function(header) {
        var text = (header.textContent || header.innerText || "").trim();
        if (text === sel) {
            header.style.borderColor = "red";
            var link = header.querySelector('a');
            if (link) link.style.color = "red";
            setTimeout(function() {
                header.style.borderColor = "#dcd637";
                if (link) link.style.color = "#dcd637";
            }, 2000);
        }
    });
}
