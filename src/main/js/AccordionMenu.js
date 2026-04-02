'use strict';

(function() {
    var e = React.createElement;

    function PageItem(props) {
        var page = props.page;

        if (page.active) {
            return e(React.Fragment, null,
                page.categories.map(function(cat, i) {
                    return e('li', { key: i, className: 'selactive' }, cat);
                })
            );
        }

        return e('li', null,
            e('a', { href: page.href },
                e('ul', null,
                    page.categories.map(function(cat, i) {
                        return e('li', { key: i }, cat);
                    })
                )
            )
        );
    }

    function FamilyItem(props) {
        var family = props.family;
        var state = React.useState(family.open || false);
        var expanded = state[0];
        var setExpanded = state[1];

        if (family.single) {
            return e('li', null,
                e('ul', { className: 'menusecopen single' },
                    family.pages.map(function(page, i) {
                        return e(PageItem, { key: i, page: page });
                    })
                )
            );
        }

        return e('li', null,
            e('a', {
                onClick: function(ev) {
                    ev.preventDefault();
                    setExpanded(!expanded);
                },
                style: { cursor: 'pointer' }
            }, family.name),
            expanded ? e('ul', { className: 'menusecopen' },
                family.pages.map(function(page, i) {
                    return e(PageItem, { key: i, page: page });
                })
            ) : null
        );
    }

    function AccordionSection(props) {
        var expanded = props.expanded;
        var onToggle = props.onToggle;

        return e('div', null,
            e('h3', {
                className: 'accordion-header' + (expanded ? ' accordion-header-active' : ''),
                onClick: onToggle
            },
                e('a', null, props.name)
            ),
            expanded ? e('div', { className: 'accordion-content' },
                e('ul', { className: 'menusec1' },
                    props.families.map(function(family, i) {
                        return e(FamilyItem, { key: i, family: family });
                    })
                )
            ) : null
        );
    }

    function AccordionMenu(props) {
        var data = props.data;
        if (!data || !data.sections) return null;

        var state = React.useState(data.activeSection != null ? data.activeSection : -1);
        var openIndex = state[0];
        var setOpenIndex = state[1];

        return e(React.Fragment, null,
            data.sections.map(function(section, i) {
                return e(AccordionSection, {
                    key: i,
                    name: section.name,
                    families: section.families,
                    expanded: openIndex === i,
                    onToggle: function() { setOpenIndex(openIndex === i ? -1 : i); }
                });
            })
        );
    }

    window.AccordionMenu = AccordionMenu;
})();
