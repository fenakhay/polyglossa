use std::hash::{Hash, Hasher};
use std::ops::RangeInclusive;
use std::ptr;

use crate::Catalog;

/// The place of a language in its catalog's table of languages.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub(crate) struct LanguageId(pub(crate) usize);

/// The place of a family in its catalog's table of families.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub(crate) struct FamilyId(pub(crate) usize);

/// The place of a script in its catalog's table of scripts.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord)]
pub(crate) struct ScriptId(pub(crate) usize);

/// A code one entry gives for another, resolved when the catalog is read: to the entry when the catalog has
/// it, or kept as written when it does not.
#[derive(Debug, Clone, PartialEq, Eq)]
pub(crate) enum Link<Id> {
    Known(Id),
    Unknown(String),
}

impl<Id: Copy> Link<Id> {
    pub(crate) fn known(&self) -> Option<Id> {
        match self {
            Link::Known(id) => Some(*id),
            Link::Unknown(_) => None,
        }
    }
}

/// An entry of a catalog, by its place there: what [`Language`](crate::Language),
/// [`LanguageFamily`](crate::LanguageFamily) and [`Script`](crate::Script) wrap. Two are equal when they are
/// the same entry of the same catalog.
#[derive(Debug, Clone, Copy)]
pub(crate) struct Ref<'cat, Id> {
    pub(crate) catalog: &'cat Catalog,
    pub(crate) id: Id,
}

impl<Id: PartialEq> PartialEq for Ref<'_, Id> {
    fn eq(&self, other: &Self) -> bool {
        ptr::eq(self.catalog, other.catalog) && self.id == other.id
    }
}

impl<Id: Eq> Eq for Ref<'_, Id> {}

impl<Id: Hash> Hash for Ref<'_, Id> {
    fn hash<H: Hasher>(&self, state: &mut H) {
        ptr::hash(self.catalog, state);
        self.id.hash(state);
    }
}

/// Whether one of `ranges`, sorted and apart, holds `code_point`.
pub(crate) fn holds(ranges: &[RangeInclusive<u32>], code_point: u32) -> bool {
    let at = ranges.partition_point(|range| *range.end() < code_point);
    ranges
        .get(at)
        .is_some_and(|range| range.contains(&code_point))
}
