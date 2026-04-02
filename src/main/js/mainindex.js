

function renderNav() {
    var navRoot = document.getElementById('topnav-root');
    if (navRoot && window.TopNav && window.navItems) {
        ReactDOM.createRoot(navRoot).render(
            React.createElement(TopNav, { items: window.navItems })
        );
    }
}

function renderAccordion() {
    var accRoot = document.getElementById('accordion-root');
    if (accRoot && window.AccordionMenu && window.treeMenuData) {
        var root = ReactDOM.createRoot(accRoot);
        root.render(
            React.createElement(AccordionMenu, { data: window.treeMenuData })
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
